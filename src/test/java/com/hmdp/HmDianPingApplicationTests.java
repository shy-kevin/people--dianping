package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Random;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    ShopServiceImpl shopService;
    @Test
    public void contextLoads() {
       shopService.saveShop2Redis(1L, 20L);
    }

}
