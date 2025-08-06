package com.mlab.askvistax.config;

import com.mlab.askvistax.interceptors.WsHandshakeInterceptor;
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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册WebSocket处理器
        registry.addHandler(videoStreamHandler, "/interviewing")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    // 配置二进制缓冲区大小
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(65536);    // 文本数据的缓冲区大小设置为 64KB
        container.setMaxBinaryMessageBufferSize(524288); // 二进制数据的缓冲区大小设置为 512KB
        return container;
    }

}
