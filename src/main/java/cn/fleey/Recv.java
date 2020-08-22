package cn.fleey;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class Recv {

    public static JsonObject config = null;

    public static Connection rabbitMqConnection = null;

    //成功数量
    public static int todaySuccessCount = 0;
    //失败数量
    public static int todayFailCount = 0;
    //date
    public static String date = "";
    //最后执行数据统计时间
    private static long currentTimeMillis = 0;

    //数据统计函数执行间隔
    private static int execDataStaticTimeMillis = 10 * 1000;

    public static void main(String[] argv) throws Exception {

        motd();

        loadConfig();

        List<CronBucket> cronData = new ArrayList<CronBucket>();

        config.get("cronQueue").getAsJsonArray().forEach(tempData -> {
            JsonObject jsonObject = tempData.getAsJsonObject();
            cronData.add(new CronBucket(jsonObject.get("name").getAsString(), jsonObject.get("delayerTime").getAsInt()));
        });

        Connection connection = getRabbitMqConnection();

        int i = 0;
        CronOrderCallbackThread[] orderCallbackThreads = new CronOrderCallbackThread[cronData.size()];
        for (CronBucket param : cronData) {
            i++;
            if (i != cronData.size())
                orderCallbackThreads[i - 1] = new CronOrderCallbackThread(connection, cronData.get(i - 1), cronData.get(i));
            else
                orderCallbackThreads[i - 1] = new CronOrderCallbackThread(connection, cronData.get(i - 1));
        }

        try {
            for (CronOrderCallbackThread thread : orderCallbackThreads) {
                thread.start();
            }
            Utils.log("System", "start run queues...");

            while (true) {
                for (CronOrderCallbackThread thread : orderCallbackThreads) {
                    if (!thread.isAlive()) {
                        Utils.log("thread_" + thread.nowBucket.getName(), "this is thread is die...");
                    }
                }
                Thread.sleep(1000);

                echoStaticsData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void echoStaticsData() {
        //获取当前时间 进行判断是否需要执行此函数
        long nowCurrentTimeMillis = System.currentTimeMillis();

        //判断是否超过*秒 此函数*秒执行一次 ，然后刷新最后执行时间
        if (nowCurrentTimeMillis > currentTimeMillis + execDataStaticTimeMillis) {
            currentTimeMillis = nowCurrentTimeMillis;
        } else {
            return;
        }


        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String nowDay = df.format(new Date());

        //刷新当日数据
        if (!nowDay.equals(date)) {
            todayFailCount = 0;
            todaySuccessCount = 0;
            date = nowDay;
        }

        Utils.log("dataStaticsData", "success => " + todaySuccessCount + "，fail= > " + todayFailCount);
    }

    private static void motd() {
        String motd = "================================================================\n" +
                "  _____         _                            _____              \n" +
                " |  __ \\       | |                          |  __ \\             \n" +
                " | |  | |  ___ | |  __ _  _   _   ___  _ __ | |__) | ___   __ _ \n" +
                " | |  | | / _ \\| | / _` || | | | / _ \\| '__||  _  / / _ \\ / _` |\n" +
                " | |__| ||  __/| || (_| || |_| ||  __/| |   | | \\ \\|  __/| (_| |\n" +
                " |_____/  \\___||_| \\__,_| \\__, | \\___||_|   |_|  \\_\\\\___| \\__, |\n" +
                "                           __/ |                             | |\n" +
                "                          |___/     Author:Fleey.2020.8.22    |_|\n" +
                "================================================================";
        System.out.println(motd);
        //out motd
    }

    private static void loadConfig() {
        String defaultConfig = "{\"execDataStaticTimeMillis\":5000,\"rabbitMQ\":{\"host\":\"127.0.0.1\",\"port\":5672,\"username\":\"guest\",\"password\":\"guest\",\"vhost\":\"/\"},\"cronQueue\":[{\"name\":\"15_1\",\"delayerTime\":\"5000\"},{\"name\":\"15_2\",\"delayerTime\":\"5000\"},{\"name\":\"30_1\",\"delayerTime\":\"30000\"}]}";
        //default config
        try {
            String filePath = System.getProperty("user.dir") + File.separator + "DelayerReqConf.json";

            File configFile = new File(filePath);
            if (!configFile.exists() || !configFile.isFile()) {
                FileOutputStream outputStream = new FileOutputStream(filePath);
                outputStream.write(defaultConfig.getBytes());
                outputStream.close();
                Utils.log("System", "not found config, use default config file.");
            }

            FileInputStream fileInputStream = new FileInputStream(filePath);

            StringBuilder tempData = new StringBuilder();
            byte[] tempbytes = new byte[1024];
            int byteread = 0;

            while ((byteread = fileInputStream.read(tempbytes)) != -1) {
                String str = new String(tempbytes, 0, byteread);
                tempData.append(str);
            }

            config = (JsonObject) JsonParser.parseString(tempData.toString());

            if(config.get("execDataStaticTimeMillis") != null){
                execDataStaticTimeMillis = config.get("execDataStaticTimeMillis").getAsInt();
            }

            Utils.log("System", "load config success.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Connection getRabbitMqConnection() throws Exception {
        if (rabbitMqConnection == null) {
            if (config == null)
                throw new Exception("read config fail.");

            JsonObject rabbitMQConfig = config.get("rabbitMQ").getAsJsonObject();

            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(rabbitMQConfig.get("host").getAsString());
            connectionFactory.setUsername(rabbitMQConfig.get("username").getAsString());
            connectionFactory.setPassword(rabbitMQConfig.get("password").getAsString());
            connectionFactory.setPort(rabbitMQConfig.get("port").getAsInt());
            connectionFactory.setVirtualHost(rabbitMQConfig.get("vhost").getAsString());
            try {
                rabbitMqConnection = connectionFactory.newConnection();
            } catch (IOException | TimeoutException e) {
                Utils.log("System", e.getMessage());
                throw new Exception(e.getMessage());
            }
        }
        return rabbitMqConnection;
    }
}