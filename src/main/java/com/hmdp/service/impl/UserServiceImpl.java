package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
// 为解决session在多台服务器之间不能共享的问题，将code和用户信息存储在redis中
//    @Override
//    public Result sendCode(String phone, HttpSession session) {
//        //校验手机号
//        if(RegexUtils.isPhoneInvalid(phone)){
//            return Result.fail("手机格式错误");
//        }
//        // 符合就生成校验码
//        String code = RandomUtil.randomString(6);
//
//        // 保存验证码到session中，服务器内，如果没有seesion，服务器就会创建一个session，然后吧sessionid返回给客户端，以后客户端请求就会带着这个sessionid来请求服务器，服务器就根据这个sessionid来获取或修改数据
//        session.setAttribute("code",code);
//
//        // 发送验证码到手机上，这里没有真的来实现发送功能，就是用日志输出一下
//        log.debug("验证码:{}",code);
//
//        return Result.ok();
//    }
//
//    @Override
//    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        //校验手机号
//        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
//            return Result.fail("手机格式错误");
//        }
//        // 校验验证码
//        Object session_code = session.getAttribute("code");  // 从session获取code，如果之前用户请求了验证码，服务器就会创建session，返回sessionid给客户端，客户端再次请求登录的时候就会携带这个sessionid，服务器就会根据这个sessionid来找对应session里的数据
//        String user_code = loginForm.getCode();
//        if(session_code == null || !session_code.equals(user_code)){    // 如果session_code为null就说明根本没有session
//            return Result.fail("验证码错误");
//        }
//        // 一致就根据手机号查询用户
//        User user = query().eq("phone",loginForm.getPhone()).one();  // 根据前端传来的phone去数据库中查询数据，看是否有该手机用户，如果有就登录，没有就自动创建一个用户
//
//        // 判断用户是否存在
//        if(user == null){
//            user = createUserWithPhone(loginForm.getPhone());  // 没有用户就自动创建
//        }
//        // 保存用户信息到session中
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));  // 然后再把用户信息保存到session中,只保存DTO，防止信息泄露,同时减少保存session的内存占用
//
//        return Result.ok();
//    }
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式错误");
        }
        // 符合就生成校验码
        String code = RandomUtil.randomString(6);

        // 保存验证码到redis中,并设置过期时间2分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 发送验证码到手机上，这里没有真的来实现发送功能，就是用日志输出一下
        log.debug("验证码:{}",code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机格式错误");
        }
        // 校验验证码
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+loginForm.getPhone());  // redis中获取验证码
        String user_code = loginForm.getCode();
        if(code == null || !code.equals(user_code)){    // 如果code为null就说明根本没有发送验证码或者验证码已经过期
            return Result.fail("验证码错误");
        }
        // 一致就根据手机号查询用户
        User user = query().eq("phone",loginForm.getPhone()).one();  // 根据前端传来的phone去数据库中查询数据，看是否有该手机用户，如果有就登录，没有就自动创建一个用户

        // 判断用户是否存在
        if(user == null){
            user = createUserWithPhone(loginForm.getPhone());  // 没有用户就自动创建
        }
        // 保存用户信息到redis中
        // 随机生成token，作为登录令牌，也作为redis的key
        String token = UUID.randomUUID().toString(true); // tostring中带上参数true表示生成的UUID里不带中划线
        // 将User对象转化为Hash存储
        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user,userDTO);
        Map<String,Object> map = new HashMap<>();
        map.put("id",userDTO.getId().toString());
        map.put("nickName",userDTO.getNickName());
        map.put("icon",userDTO.getIcon());
//        Map<String,Object> map = BeanUtil.beanToMap(userDTO);  // 这个简单
        // 存储到redis中,同时要设置过期时间
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,map);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 返回token
        return Result.ok(token);
    }

    @Override
    public Result queryUserById(Long id) {
        User user = query().eq("id", id).one();
        if(user == null){
            return Result.ok();
        }
        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user,userDTO);
        return Result.ok(userDTO);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));  // 创建一个随机的NickName
        // 保存用户
        save(user);
        return user;
    }


}
