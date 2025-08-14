package com.mlab.askvistax.config;

import com.mlab.askvistax.interceptors.WsHandshakeInterceptor;
import com.mlab.askvistax.websocket.AudioStreamHandler;
import com.mlab.askvistax.websocket.VideoStreamHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Autowired
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Autowired
    private VideoStreamHandler videoStreamHandler;

    @Autowired
    private AudioStreamHandler audioStreamHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册WebSocket处理器
        registry.addHandler(audioStreamHandler, "/audio")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOrigins("*");

        registry.addHandler(videoStreamHandler, "/interviewing")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    // 配置二进制缓冲区大小
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);  // 10MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024); // 10MB，提升二进制缓冲区大小
        container.setAsyncSendTimeout(60000L);                     // 异步发送超时60秒，默认可能较短
        return container;
    }

}
