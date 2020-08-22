package cn.fleey;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

class ConnectionUtil {

    private static final String RABBIT_HOST = "localhost";

    private static final String RABBIT_USERNAME = "admin";

    private static final String RABBIT_PASSWORD = "admin";

    private static final String RABBIT_VHOST = "/";

    private static final int RABBIT_PORT = 5672;

    private static Connection connection = null;

    static Connection getConnection() {
        if (connection == null) {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(RABBIT_HOST);
            connectionFactory.setUsername(RABBIT_USERNAME);
            connectionFactory.setPassword(RABBIT_PASSWORD);
            connectionFactory.setPort(RABBIT_PORT);
            connectionFactory.setVirtualHost(RABBIT_VHOST);
            try {
                connection = connectionFactory.newConnection();
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        }
        return connection;
    }

}