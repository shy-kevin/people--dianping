package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
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
    @Autowired
    private IFollowService followService;


    @Override
    public Result queryBlog(Long id) {
        // 查询blog和blog的用户信息
        Blog blog = query().eq("id", id).one();
        if (blog == null) {
            return Result.ok();
        }
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

    @Override
    public Result queryUserById(Long id, Integer current) {
        Page<Blog> blogs = query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = blogs.getRecords();

        return Result.ok(records);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean success = save(blog);
        if(!success){
            return Result.fail("新增笔记失败!!!");
        }
        // 查询关注该用户的用户
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow follow : followUserId){
            // 将blog推送给这些用户
            Long userId = follow.getUserId();
            // 把博客id存到redis中定义的收件箱
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + userId, blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询收件箱  TypedTuple是一个元组，包含Zset的value和score
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, max, offset, 2);
        // 3.解析数据blogId，时间戳，offset（跟上次查询的最小值相同的个数）
        if(typedTuples == null || typedTuples.size() == 0){
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>();
        List<Blog> blogs = new ArrayList<>();
        long min = 0;
        int count = 0;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Long id = Long.valueOf(typedTuple.getValue());
            ids.add(id);
            long score = typedTuple.getScore().longValue();
            if(score == min){
                count++;
            }else{
                min = score;
                count = 1;
            }
            // 4. 根据id查询blog
            Blog blog = query().eq("id", id).one();
            // 查询博客是否已点赞
            boolean islike = islike(blog.getId());
            if (islike) {
                blog.setIsLike(true);
            }else{
                blog.setIsLike(false);
            }
            // 查询博主的用户信息
            User user = userService.getById(blog.getUserId());
            blog.setIcon(user.getIcon());
            blog.setName(user.getNickName());
            blogs.add(blog);
        }

        // 5. 封装并返回
        ScrollResult rs = new ScrollResult();
        rs.setList(blogs);
        rs.setMinTime(min);
        rs.setOffset(count);
        return Result.ok(rs);
    }
}
