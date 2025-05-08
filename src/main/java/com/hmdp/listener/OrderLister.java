package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderLister {
    @Autowired
    private VoucherOrderServiceImpl voucherOrderService;

    @RabbitListener(queues = "order.queue")
    public void listen(VoucherOrder voucherOrder) {
        log.info("用户下单：{}", voucherOrder);
        // 写入数据库
        voucherOrderService.save(voucherOrder);

    }
}
