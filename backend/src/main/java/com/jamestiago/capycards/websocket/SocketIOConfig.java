package com.jamestiago.capycards.websocket;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

// Use @Component if you want to manage its lifecycle (start/stop) via Spring events
// Or @org.springframework.context.annotation.Configuration if just defining beans
@org.springframework.context.annotation.Configuration
public class SocketIOConfig {

    private static final Logger logger = LoggerFactory.getLogger(SocketIOConfig.class);

    @Value("${socket-server.host:localhost}") // Default to localhost if not specified
    private String host;

    @Value("${socket-server.port:9092}") // Default to port 9092 if not specified
    private int port;

    @Value("${frontend.origin}") // Add this for your React dev server
    private String frontendOrigin;

    @Bean // This annotation tells Spring to manage this SocketIOServer instance as a bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);

        config.setOrigin(frontendOrigin); // Set the allowed origin for CORS

        final SocketIOServer server = new SocketIOServer(config);
        logger.info("Socket.IO server configured on host: {}, port: {}", host, port);
        return server;
    }

    // If you want Spring to manage the server's lifecycle (start/stop)
    // you might need a separate component that injects the server and starts it.
    // For now, we'll create a separate component to start it.
}
