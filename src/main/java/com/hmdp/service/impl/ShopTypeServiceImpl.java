package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 从redis中查询店铺类型
        String shoptype = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOPTYPE_KEY);
        // 判断是否存在
        if(StrUtil.isNotBlank(shoptype)){
            // 如果存在返回
            JSONArray objects = JSONUtil.parseArray(shoptype);
            List<ShopType> shopTypeList = JSONUtil.toList(objects, ShopType.class);   // Json转list的方法
            return Result.ok(shopTypeList);
        }
        // 不存在去数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 数据库中存在返回
        if (typeList != null && typeList.size() > 0) {
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOPTYPE_KEY, JSONUtil.toJsonStr(typeList));
            return Result.ok(typeList);
        }
        // 不存在报错
        return Result.fail("404");
    }
}
