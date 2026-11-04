package org.unibl.etf.mdp.vservice.supplier.util;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class ConnectionFactoryUtil {

	public static Connection createConnection() throws IOException, TimeoutException {
		
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(PropertiesUtil.getProperty("MQ_HOST"));
		factory.setUsername(PropertiesUtil.getProperty("MQ_USERNAME"));
		factory.setPassword(PropertiesUtil.getProperty("MQ_PASSWORD"));
		
		return factory.newConnection();
	}
}
