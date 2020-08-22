package cn.fleey;

public class CronBucket {
    private String name;
    private int delayerTime;

    /**
     * @param name        string 名称
     * @param delayerTime int 延迟时间
     */
    public CronBucket(String name, int delayerTime) {
        this.delayerTime = delayerTime;
        this.name = name;
    }

    public int getDelayerTime() {
        return delayerTime;
    }

    public void setDelayerTime(int delayerTime) {
        this.delayerTime = delayerTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
