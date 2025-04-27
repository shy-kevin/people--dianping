package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static  final long BEGIN_TIMESTAMP = 1640995200L;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // redis生成全局唯一ID
    public long nextId(String keyprefix){
        // 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;

        // 生成序列号
        // 获取当前日期
        String localDate = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyprefix + ":" + localDate);

        // 拼接返回
        return timestamp << 32 | count;   // timestamp向左移32位，在将count与其或运算
    }

}
