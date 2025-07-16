package com.mlab.askvistax.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// TODO 实现心跳检测超时断连等意外情况
@Component
@Slf4j
public class VideoStreamHandler extends AbstractWebSocketHandler {
    // 保存每个连接对应的文件写入流，线程安全的哈希表容器
    private final Map<String, FileOutputStream> sessionOutputMap = new ConcurrentHashMap<>();

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws IOException {
        String sessionId = session.getId();
        if (message instanceof BinaryMessage binaryMessage) {
            byte[] data = binaryMessage.getPayload().array();

            // 根据sessionId获取写入流或者创建写入流存入sessionOutputMap
            FileOutputStream fos = sessionOutputMap.computeIfAbsent(sessionId, id -> {
                try {
                    String filename = "video_" + id + "_" + System.currentTimeMillis() + ".webm";
                    File file = new File(filename);
                    log.info("视频传输开始，文件名: {}", filename);
                    return new FileOutputStream(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            fos.write(data);
            log.info("接收到视频片段: {} 字节", data.length);
        }
        else if (message instanceof TextMessage textMessage) {
            String payload = textMessage.getPayload();
            log.info("text: {}", payload);
            if ("__END__".equals(payload)) {
                log.info("session连接: {}收到结束信号", sessionId);
                session.close(CloseStatus.NORMAL.withReason("Transmission complete"));
            }
        }
        else {
            log.warn("收到不支持的消息类型，关闭连接");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unsupported message type"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        log.info("连接: {}关闭, 原因: {}", sessionId, status);
        // 关闭流并清理资源
        closeStream(sessionId);
    }

    private void closeStream(String sessionId) {
        FileOutputStream fos = sessionOutputMap.remove(sessionId);
        if (fos != null) {
            try {
                fos.close();
                log.info("视频文件写入完成，连接 {} 的视频已保存", sessionId);
            } catch (IOException e) {
                log.error("关闭写入流失败: {}", e.getMessage(), e);
            }
        }
    }

}
