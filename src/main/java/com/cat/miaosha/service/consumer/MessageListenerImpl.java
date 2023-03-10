package com.cat.miaosha.service.consumer;


import cn.hutool.db.sql.Order;
import com.cat.miaosha.dao.ItemDOMapper;
import com.cat.miaosha.dao.ItemStockDOMapper;
import com.cat.miaosha.dao.OrderDOMapper;
import com.cat.miaosha.dao.StockLogDOMapper;
import com.cat.miaosha.entity.OrderDO;
import com.cat.miaosha.entity.StockLogDO;
import com.cat.miaosha.utils.JsonUtils;
import com.cat.miaosha.utils.RedisUtils;
import com.cat.miaosha.utils.SpringContextUtils;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MessageListenerImpl implements MessageListenerConcurrently {

    private final static String ORDER_PREFIX = "order_";
    private final ItemStockDOMapper itemStockDOMapper = SpringContextUtils.getBean("itemStockDOMapper", ItemStockDOMapper.class);
    private final OrderDOMapper orderDOMapper = SpringContextUtils.getBean("orderDOMapper", OrderDOMapper.class);
    private final StockLogDOMapper stockLogDOMapper = SpringContextUtils.getBean("stockLogDOMapper", StockLogDOMapper.class);
    private final ItemDOMapper itemDOMapper = SpringContextUtils.getBean("itemDOMapper", ItemDOMapper.class);

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
        try {
            MessageExt messageExt = list.get(0);

            String body = new String(messageExt.getBody());
            Map<String, Object> msg = JsonUtils.jsonToObject(body, Map.class);
            Long itemId = (Long) msg.get("itemId");
            Integer amount = (Integer) msg.get("amount");
            String stockLogId = (String) msg.get("stockLogId");
            String orderId = (String) msg.get("orderId");
            Boolean b = RedisUtils.hasKey(ORDER_PREFIX + orderId);
            if(b){
                // ???????????????
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            int updateCount = itemStockDOMapper.decreaseStock(itemId, amount);
            if(updateCount > 0){
                // ??????????????????
                OrderDO orderDO = new OrderDO();
                orderDO.setId(orderId);
                orderDO.setStatus(2);
                orderDOMapper.updateByPrimaryKeySelective(orderDO);
                itemDOMapper.increaseSales(itemId,amount);
            }else{
                // ??????????????????
                OrderDO orderDO = new OrderDO();
                orderDO.setId(orderId);
                orderDO.setStatus(3);
                // ??????????????????
                StockLogDO stockLogDO = new StockLogDO();
                stockLogDO.setStockLogId(stockLogId);
                stockLogDO.setStatus(3);
                stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                orderDOMapper.updateByPrimaryKeySelective(orderDO);
            }
            // TODO: 2023/1/14 ????????????????????????????????????????????????
            RedisUtils.set(ORDER_PREFIX + orderId, " ", 15, TimeUnit.MINUTES);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            // ??????????????????????????????????????? n ??????????????????????????????????????????????????????????????????????????????????????????????????????
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }
}
