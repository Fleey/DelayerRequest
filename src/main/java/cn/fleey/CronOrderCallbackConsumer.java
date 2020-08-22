package cn.fleey;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class CronOrderCallbackConsumer extends DefaultConsumer {

    private CronBucket nextBucket = null;

    private Connection connection = null;

    private OkHttpClient okHttpClient = null;

    CronOrderCallbackConsumer(Channel channel) {
        this(channel, null);
    }

    CronOrderCallbackConsumer(Channel channel, CronBucket nextBucket) {
        super(channel);

        this.nextBucket = nextBucket;
        this.connection = channel.getConnection();

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS);
        this.okHttpClient = okHttpClientBuilder.build();
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        String msg = new String(body);

        sendRequest(msg);
    }

    private void sendRequest(String content) {
        try {
            JsonObject jsonObject = (JsonObject) JsonParser.parseString(content);

            if (!jsonObject.has("method") || !jsonObject.has("url"))
                return;
            //check data
            String method = jsonObject.get("method").getAsString().toLowerCase();
            String requestUrl = jsonObject.get("url").getAsString();

            Request.Builder requestBuild = new Request.Builder().removeHeader("User-Agent");

            if (!jsonObject.has("headers")) {
                requestBuild.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.131 Safari/537.36");
                //设置默认userAgent
            } else {
                JsonElement headersElement = jsonObject.get("headers");
                if (!headersElement.isJsonArray())
                    return;
                //如果不是数组格式
                JsonArray jsonArray = headersElement.getAsJsonArray();

                jsonArray.forEach(dataMap -> {
                    JsonObject tempData = dataMap.getAsJsonObject();
                    if (tempData.has("key") && tempData.has("value"))
                        requestBuild.addHeader(tempData.get("key").getAsString(), tempData.get("value").getAsString());
                });
                //遍历设置header
            }
            //设置header

            if (jsonObject.has("param")) {
                JsonElement paramElement = jsonObject.get("param");
                do {
                    if (!paramElement.isJsonObject())
                        break;

                    if (method.equals("get")) {
                        StringBuilder param = new StringBuilder();


                        for (Map.Entry<String, JsonElement> entry : jsonObject.get("param").getAsJsonObject().entrySet()) {
                            JsonElement value = entry.getValue();
                            try {
                                param.append(entry.getKey()).append("=").append(URLEncoder.encode(value.getAsString(), "UTF-8")).append("&");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                        }
                        param = new StringBuilder(param.substring(0, param.length() - 1));
                        requestUrl += "?" + param.toString();
                        requestBuild.url(requestUrl);
                    } else if (method.equals("post")) {
                        FormBody.Builder formBodyBuilder = new FormBody.Builder();
                        for (Map.Entry<String, JsonElement> entry : jsonObject.get("param").getAsJsonObject().entrySet()) {
                            JsonElement value = entry.getValue();
                            formBodyBuilder.add(entry.getKey(), value.getAsString());
                        }
                        FormBody formBody = formBodyBuilder.build();
                        requestBuild.post(formBody).url(requestUrl);
                    }
                } while (false);
            }

            Request request = requestBuild.build();
            Call call = okHttpClient.newCall(request);
            call.enqueue(new HttpSyncCallback(connection, nextBucket, content));
        } catch (Exception e) {
            Utils.log("CronOrderCallbackConsumer", "sendRequest Error => " + e.getMessage());
        }
    }

}
