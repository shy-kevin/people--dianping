package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;  // 初始化加载lua脚本，用静态代码块加载
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    @Override
    public Result orderSeckillVoucher(Long voucherId) throws InterruptedException {
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),userId.toString());
        int r = result.intValue();
        if(r != 0){
            return Result.fail(r == 1 ?"库存不足":"请勿重复下单");
        }
        // r为0，说明有购买资格，可以下单，生成一个订单id
        long orderId = redisIdWorker.nextId("order");
        // TODO 放到阻塞队列中，开启另一个线程去数据库写订单

        return Result.ok(orderId);
    }

//    @Override
//    public Result orderSeckillVoucher(Long voucherId) throws InterruptedException {
//        // 查询优惠卷信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        LocalDateTime now = LocalDateTime.now();
//        // 判断时间
//        if(now.isAfter(seckillVoucher.getEndTime()) || now.isBefore(seckillVoucher.getBeginTime())) {
//            return Result.fail("活动不存在");
//        }
//        // 判断库存
//        if(seckillVoucher.getStock()<0)
//            return Result.fail("库存不足");
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()){  // 根据userId来获取锁
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();  // 获取代理对象,通过代理对象来执行下面的方法，防止事务失效（不是很理解）
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        // 自己实现的锁
////        SimpleRedisLock redisLock = new SimpleRedisLock("order"+userId,stringRedisTemplate);  // 在key中加上用户id，锁住同一个用户
////        boolean b = redisLock.tryLock(120L);
////        if(!b) {// 获取锁失败
////            return Result.fail("请勿重复下单！");
////        }
//        // 获取锁成功
////        try {
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();  // 获取代理对象,通过代理对象来执行下面的方法，防止事务失效（不是很理解）
////            return proxy.createVoucherOrder(voucherId);
////        } catch (IllegalStateException e) {
////            throw new RuntimeException(e);
////        } finally {
////            redisLock.unlock();  // 释放锁
////        }
//
//        // redisson框架实现的锁
//        //获取锁，可重入的
//        RLock lock = redissonClient.getLock("lock:order" + userId);
//        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS); //三个参数分别是，获取锁的最大等待时间，锁的自动释放时间，时间单位
//        if(!isLock) {// 获取锁失败
//            return Result.fail("不允许重复下单");
//        }
//        // 获取锁成功
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();  // 获取代理对象,通过代理对象来执行下面的方法，防止事务失效（不是很理解）
//            return proxy.createVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }
//    }

    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 看数据库中用户是否已经购买该优惠卷，这样高并发下还是有问题,所以需要加锁
        int count = query().eq("voucher_id", voucherId).eq("user_id",userId).count();
        if(count > 0)
            return Result.fail("您已购买！");

        // 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
//                .eq("stock", seckillVoucher.getStock())  // 乐观锁，在修改库存前，判断数据库里库存信息是否是跟自己之前查询的一样，一样表示没有被别人修改，则可以修改
                .gt("stock",0)   // 乐观锁改进，不用一定要与之前相等不然失败率太高，直接判断大于0就行
                .update();
        if(!success)
            return Result.fail("库存扣减失败");
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("voucherorder"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        // 写入数据库
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
