# DelayerRequest
利用 `RabbitMQ` 死信队列进行延迟 http请求，当请求失败时会进行经过一定时间进行补发请求。

## 具体玩法

第一步 安装 `Java` 推荐1.8+

第二步 启动程序，让其自动生成配置文件，或者你可以自行在程序所在目录进行自定编写配置文件。

配置文件大概长这个样子 注意其配置文件名称必须叫 （`DelayerReqConf.json`）

```config
{
  "execDataStaticTimeMillis":10000,
  "rabbitMQ": {
    "host": "rabbitMQ 地址",
    "port": rabbitMQ 端口（int）,
    "username": "rabbitMQ 账户",
    "password": "rabbitMQ 密码",
    "vhost": "rabbitMQ 当vhost路径"
  },
  "cronQueue": [
    {
      "name": "15_1",
      "delayerTime": "15000"
    },
    {
      "name": "15_2",
      "delayerTime": "15000"
    },
    {
      "name": "30_1",
      "delayerTime": "30000"
    }
    ......
  ]
}
```

其中cronQueue里面是存放死信队列内容，当第一个队列请求失败就会等待n秒后进行第二个队列请求

```config
{
  "name":"队列名称",
  "delayerTime":"延迟等待时间 毫秒"
}
```

其中 execDataStaticTimeMillis 这个配置参数为 多少秒输出一次 当天的请求成功与失败的 数据统计信息，如果你进行填写此字段则不进行输出。

## 压入队列教程

你可以选择你想直接操作的队列，或者直接选择第一个队列名称 让其自动失败则按顺序跑下去。

这里使用PHP作为代码例子，其他语言同理

```php
  /**
     * 自动队列回调
     * @param int $uid
     * @param string $url
     * @param string $method
     * @param array $requestData
     */
    public static function addCallBackLog(string $url, string $method = 'get', array $requestData = [])
    {
        if (empty($requestData) && $method == 'get') {
            $parseUrl    = parse_url($url);
            $url         = $parseUrl['scheme'] . '://' . $parseUrl['host'] . $parseUrl['path'];
            $requestData = \GuzzleHttp\Psr7\parse_query($parseUrl['query']);
        }
        //构建兼容层

        $rabbitMQConfig = config('cache.rabbitMQ');
        $connection     = new AMQPStreamConnection($rabbitMQConfig['host'], $rabbitMQConfig['port'], $rabbitMQConfig['username'], $rabbitMQConfig['password'], $rabbitMQConfig['vhost']);
        $channel        = $connection->channel();

        $bucketName = '15_1';

        $orderExchangeName = 'order_exchange_' . $bucketName;
        $dlxExchangeName   = 'dlx.exchange.' . $bucketName;
        $orderQueueName    = 'order_queue_' . $bucketName;
        $dlxQueueName      = 'dlx.queue.' . $bucketName;
        $orderRoutingKey   = 'order.' . $bucketName . ".#";

        {
            $channel->exchange_declare($orderExchangeName, 'topic', false, true, false);
            $channel->queue_declare($orderQueueName, false, true, false, false, false, new AMQPTable([
                'x-dead-letter-exchange' => $dlxExchangeName,
                'x-message-ttl'          => 15 * 1000
            ]));
            $channel->queue_bind($orderQueueName, $orderExchangeName, $orderRoutingKey);
            //这里是队列消息
        }


        {
            $channel->exchange_declare($dlxExchangeName, 'topic', false, true, false);
            $channel->queue_declare($dlxQueueName, true, false, false);
            $channel->queue_bind($dlxQueueName, $dlxExchangeName, $orderRoutingKey);
            //死信队列
        }
        $msg = [
            'url'    => $url,
            'method' => $method,
            'param'  => $requestData
        ];
        $msg = json_encode($msg);
        $msg = new AMQPMessage($msg);
        $channel->basic_publish($msg, $orderExchangeName, "order." . $bucketName . ".save", false, false);
    }
```

其核心在于，往消息队列插入此json内容

```json
{
    "url":"http://baidu.com",
    "method":"PUT",
    "param":{
        "99999":"xxxxxxx",
        "test":"8888"
    }
}
```
