package cn.fleey;

import java.text.SimpleDateFormat;
import java.util.Date;

class Utils {
    /**
     * 输出日志
     *
     * @param tagName string tag名称
     * @param message string 日志信息
     */
    static void log(String tagName, String message) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("[" + df.format(new Date()) + "][" + tagName + "] " + message);
    }
}
