package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private BlogCommentsMapper blogCommentsMapper;


    @Override
    public Result queryBlog(Long id) {
        // 查询blog和blog的用户信息
        Blog blog = query().eq("id", id).one();
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());

        // 查询blog是否被当前用户点赞
        boolean islike = islike(id);
        if (islike) {
            blog.setIsLike(true);
        }else{
            blog.setIsLike(false);
        }

        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            // 查询blog是否被当前用户点赞
            boolean islike = islike(blog.getId());
            if (islike) {
                blog.setIsLike(true);
            }else{
                blog.setIsLike(false);
            }
        });
        return Result.ok(records);
    }

    private boolean islike(Long id){
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 判断登录用户是否已经点赞
        String key = "blog:liked:"+id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        return score!=null;
    }

    @Override
    public Result likeBlog(Long id) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 判断登录用户是否已经点赞
        String key = "blog:liked:"+id;
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score == null){
            // 如果没有点赞就可以点赞，点赞数加一,并将点赞用户加入Set集合
            // 改成用zset在添加用户进入zset的时候顺便将时间戳作为score，这样就可以实现时间的排序
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else{
            // 如果点赞了就不可以点赞，点赞数减一，并将点赞用户从Set集合移除
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryLikeBlog(Long id) {
        Set<String> top5UserId = stringRedisTemplate.opsForZSet().reverseRange(RedisConstants.BLOG_LIKED_KEY+id,0,4);
        if(top5UserId == null || top5UserId.size() == 0){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5UserId.stream().map( userid -> Long.valueOf(userid)).collect(Collectors.toList());

//        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());


        String idstr = StrUtil.join( ",",ids);
        List<UserDTO> users = userService.query().in("id",ids).last("order by field(id,"+idstr+")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());


//        List<UserDTO> users = new ArrayList<>();
//        for (Long l : ids) {
//            User user = userService.getById(l);
//            UserDTO userDTO = new UserDTO();
//            BeanUtils.copyProperties(user, userDTO);
//            users.add(userDTO);
//        }
        return Result.ok(users);
    }
}
