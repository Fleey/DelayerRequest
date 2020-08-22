package cn.fleey;

import cn.fleey.CronBucket;
import cn.fleey.CronOrderCallbackConsumer;
import com.rabbitmq.client.*;

import java.util.HashMap;
import java.util.Map;

public class CronOrderCallbackThread extends Thread {

    public CronBucket nowBucket;

    private CronBucket nextBucket;

    private Connection connection;

    public CronOrderCallbackThread(Connection connection, CronBucket nowBucket) {
        this(connection, nowBucket, null);
    }

    public CronOrderCallbackThread(Connection connection, CronBucket nowBucket, CronBucket nextBucket) {
        this.connection = connection;
        this.nowBucket = nowBucket;
        this.nextBucket = nextBucket;
    }

    @Override
    public void run() {
        try {
            final Channel channel = connection.createChannel();
            //create channel
            channel.basicQos(64);
            //limit qos
            CronOrderCallbackConsumer consumer = new CronOrderCallbackConsumer(channel, nextBucket);
            //create listen queue
            initQueue(channel);
            //init queue
            channel.basicConsume("dlx.queue." + nowBucket.getName(), true, consumer);
            // 监听队列，第二个参数：是否自动进行消息确认。
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initQueue(Channel channel) {
        String orderExchangeName = "order_exchange_" + nowBucket.getName();
        String dlxExchangeName = "dlx.exchange." + nowBucket.getName();
        String orderQueueName = "order_queue_" + nowBucket.getName();
        String dlxQueueName = "dlx.queue." + nowBucket.getName();
        String orderRoutingKey = "order." + nowBucket.getName() + ".#";

        try {
            channel.queueDelete(orderQueueName);
            channel.exchangeDelete(orderExchangeName);
            //删除之前的队列与交换机，免得初始化报错
            Map<String, Object> arguments = new HashMap<>(16);
            // 为队列设置队列交换器
            arguments.put("x-dead-letter-exchange", dlxExchangeName);
            // 设置队列中的消息 10s 钟后过期
            arguments.put("x-message-ttl", nowBucket.getDelayerTime());
            //arguments.put("x-dead-letter-routing-key", "为 dlx exchange 指定路由键，如果没有特殊指定则使用原队列的路由键");
            channel.exchangeDeclare(orderExchangeName, "topic", true, false, null);
            channel.queueDeclare(orderQueueName, true, false, false, arguments);
            channel.queueBind(orderQueueName, orderExchangeName, orderRoutingKey);

            // 创建死信交换器和队列
            channel.exchangeDeclare(dlxExchangeName, "topic", true, false, null);
            channel.queueDeclare(dlxQueueName, true, false, false, null);
            channel.queueBind(dlxQueueName, dlxExchangeName, orderRoutingKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
