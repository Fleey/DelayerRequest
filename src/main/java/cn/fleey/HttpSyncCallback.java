package cn.fleey;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MessageProperties;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HttpSyncCallback implements Callback {

    private Connection connection;
    private CronBucket nextBucket;

    private String message;

    HttpSyncCallback(Connection connection, CronBucket nextBucket, String message) {
        this.connection = connection;
        this.nextBucket = nextBucket;
        this.message = message;
    }

    @Override
    public void onFailure(Call call, IOException e) {
        if (nextBucket == null)
            return;
        pushMessage(message);
    }

    @Override
    public void onResponse(Call call, Response response) throws IOException {
        boolean isNext = true;

        if (response.isSuccessful()) {
            Pattern pattern = Pattern.compile("SUCCESS");
            int requestCode = response.code();
            if (requestCode == 200) {
                ResponseBody responseBody = response.body();
                String body = responseBody.string().replaceAll("\\r", "").replaceAll("\\n", "").toUpperCase();
                Matcher matcher = pattern.matcher(body);
                if (matcher.find()) {
                    isNext = false;
                }
                responseBody.close();
            }
        }
        response.close();

        if (isNext) {
            Recv.todayFailCount++;
        } else {
            Recv.todaySuccessCount++;
        }

        if (isNext && nextBucket != null) {
            pushMessage(message);

            Utils.log("RequestFail", "Request next buckName " + nextBucket.getName() + " domain (" + response.request().url().host() + ")");
        }
    }


    /**
     * 推送消息
     *
     * @param body string
     */
    private void pushMessage(String body) {
        try {
            Channel nextChannel = connection.createChannel();

            String orderExchangeName = "order_exchange_" + nextBucket.getName();
            String dlxExchangeName = "dlx.exchange." + nextBucket.getName();
            String orderQueueName = "order_queue_" + nextBucket.getName();
            String dlxQueueName = "dlx.queue." + nextBucket.getName();
            String orderRoutingKey = "order." + nextBucket.getName() + ".#";
            Map<String, Object> arguments = new HashMap<>(16);
            // 为队列设置队列交换器
            arguments.put("x-dead-letter-exchange", dlxExchangeName);
            // 设置队列中的消息 10s 钟后过期
            arguments.put("x-message-ttl", nextBucket.getDelayerTime());
            //arguments.put("x-dead-letter-routing-key", "为 dlx exchange 指定路由键，如果没有特殊指定则使用原队列的路由键");
            nextChannel.exchangeDeclare(orderExchangeName, "topic", true, false, null);
            nextChannel.queueDeclare(orderQueueName, true, false, false, arguments);
            nextChannel.queueBind(orderQueueName, orderExchangeName, orderRoutingKey);

            // 创建死信交换器和队列
            nextChannel.exchangeDeclare(dlxExchangeName, "topic", true, false, null);
            nextChannel.queueDeclare(dlxQueueName, true, false, false, null);
            nextChannel.queueBind(dlxQueueName, dlxExchangeName, orderRoutingKey);

            nextChannel.basicPublish(orderExchangeName, "order." + nextBucket.getName() + ".save", MessageProperties.PERSISTENT_TEXT_PLAIN, body.getBytes(StandardCharsets.UTF_8));

            try {
                nextChannel.close();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Utils.log("HttpSyncCallback", e.getCause().getMessage());
        }
    }
}
