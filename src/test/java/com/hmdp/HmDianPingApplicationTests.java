package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.SimpleRedisLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    public void contextLoads() {
        SimpleRedisLock s1 = new SimpleRedisLock("order"+1,stringRedisTemplate);
        SimpleRedisLock s2 = new SimpleRedisLock("order"+2,stringRedisTemplate);
        s1.tryLock(1000L);
        s2.tryLock(1000L);
    }

    @Test
    public void testAddGeo() {
        // 查询数据库的店铺信息
        List<Shop> shops = shopService.list();

        // 把店铺按类型分组
        Map<Long,List<Shop>> map = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批写入redis中
        map.forEach((k,v) ->{
            String key = "shop:geo:"+k;
            for (Shop shop : v) {
                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
            }
        });
    }

}
