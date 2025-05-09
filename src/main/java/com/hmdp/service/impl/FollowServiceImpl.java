package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 判断是取关还是关注
        Long userId = UserHolder.getUser().getId();
        if(BooleanUtil.isTrue(isFollow)){
            Follow follow = new Follow();
            follow.setCreateTime(LocalDateTime.now());
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean success = save(follow);
            if(success){
                stringRedisTemplate.opsForSet().add(RedisConstants.Follows+userId,id.toString());
            }
        }else{
            // 取关，删除
            boolean success = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if(success){
                stringRedisTemplate.opsForSet().remove(RedisConstants.Follows+userId,id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Follow follow = query().eq("user_id", userId).eq("follow_user_id", id).one();
        if(follow == null){
            return Result.ok(false);
        }
        return Result.ok(true);
    }

    @Override
    public Result commonFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(RedisConstants.Follows + userId, RedisConstants.Follows + id); // 通过reids查询共同关注好友的id
        if(intersect != null && !intersect.isEmpty()){   // 如果有共同关注
            List<Long> ids = intersect.stream().map(uid -> Long.valueOf(uid)).collect(Collectors.toList());
            List<User> users = userService.listByIds(ids); // 根据id去数据库查询
            List<UserDTO> userDTOs = new ArrayList<>();
            users.forEach(user -> {
                UserDTO userDTO = new UserDTO();
                BeanUtils.copyProperties(user, userDTO);
                userDTOs.add(userDTO);
            });
            return Result.ok(userDTOs);
        }
        return Result.ok();  // 没有共同关注就返回空
    }
}
