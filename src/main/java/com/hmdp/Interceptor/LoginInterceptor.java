package com.hmdp.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 获取session
//        HttpSession session = request.getSession();
//        // 获取session中的用户
//        UserDTO userDTO = (UserDTO) session.getAttribute("user");
//        // 判断用户是否存在
//        if (userDTO == null) {
//            response.setStatus(401);
//            return false;  // 没有就代表还没登录，就进行拦截
//        }
//
//        // 有就讲用户信息保存到ThreadLocal中
//        UserHolder.saveUser(userDTO);
//
//        return true;
//    }
//
//    @Override
//    //该方法将在整个请求结束之后，也就是在DispatcherServlet 渲染了对应的视图之后执行。这个方法的主要作用是用于进行资源清理工作的。
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        UserHolder.removeUser();  // 移除ThreadLocal中的变量，防止内存泄露，因为如果用系统使用线程池的话，就会有线程复用，ThreadLocal就有可能泄露
//    }
//    @Autowired // 因为这个类是自己创建的，没有交给Spring管理，所以不能用Autowired注解
    private StringRedisTemplate stringRedisTemplate;  // 只能通过构造函数来注入

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取获取请求头里的token
        String token = request.getHeader("authorization");  // 前端会将token放在请求头的authorization
        if(StrUtil.isBlank(token)){  // 如果token为空，说明没有登录
            response.setStatus(401);
            return false; 
        }
        // 根据token获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        // 判断用户是否存在
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;  // 没有就代表登录过期了
        }

        // 将查询到的Hash数据转换为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 保存用户信息到ThreadLocal中
        UserHolder.saveUser(userDTO);
        // 刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS); // 重置过期时间

        return true;
    }

    @Override
    //该方法将在整个请求结束之后，也就是在DispatcherServlet 渲染了对应的视图之后执行。这个方法的主要作用是用于进行资源清理工作的。
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();  // 移除ThreadLocal中的变量，防止内存泄露，因为如果用系统使用线程池的话，就会有线程复用，ThreadLocal就有可能泄露
    }

}
