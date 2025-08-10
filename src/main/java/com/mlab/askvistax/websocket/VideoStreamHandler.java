package com.mlab.askvistax.websocket;

import com.mlab.askvistax.utils.CommonConstants;
import com.mlab.askvistax.utils.VideoPacket;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.Block;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.opencv.video.Video;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import javax.swing.text.View;
import java.io.*;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.*;

// TODO 实现心跳检测超时断连等意外情况
// TODO 构建处理视频流线程，将其放于线程池统一处理
@Component
@Slf4j
public class VideoStreamHandler extends AbstractWebSocketHandler {
    // 保存每个session连接对应的二进制数据缓冲区队列，ConcurrentHashMap线程安全的哈希表容器
    private final Map<String, BlockingQueue<VideoPacket>> sessionQueueMap = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<VideoPacket>> sessionFileQueueMap = new ConcurrentHashMap<>();
    private final ExecutorService processorPool = Executors.newCachedThreadPool();

    // 声明一个同步信号，初始计数为1
    CountDownLatch decodeDoneLatch = new CountDownLatch(1);


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        URI uri = session.getUri();
        Map<String, Object> attributes = session.getAttributes();
        Map<String, Object> claims = (Map<String, Object>) attributes.get("claims");
        log.info("session连接: {}建立成功, URI: {}, connect_userAccount: {}, connect_userName: {}, connect_roleType: {}", sessionId, uri, claims.get("userAccount"), claims.get("userName"), CommonConstants.roleTypeMap.get(claims.get("roleType")));

        initProcess(sessionId);

    }

    // 视频流接收函数
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws IOException {
        String sessionId = session.getId();
        if (message instanceof BinaryMessage binaryMessage) {
            byte[] data = binaryMessage.getPayload().array();

            BlockingQueue<VideoPacket> queue = sessionQueueMap.get(sessionId);
            queue.offer(VideoPacket.data(data));

        }
        else if (message instanceof TextMessage textMessage) {
            String payload = textMessage.getPayload();
            log.info("text: {}", payload);
            if ("__END__".equals(payload)) {
                log.info("session连接: {}收到结束信号", sessionId);
                BlockingQueue<VideoPacket> queue = sessionQueueMap.get(sessionId);
                BlockingQueue<VideoPacket> fileQueue = sessionFileQueueMap.get(sessionId);
                // 发送结束信号给各个进程
                queue.offer(VideoPacket.poisonPill());
                fileQueue.offer(VideoPacket.poisonPill());
                session.close(CloseStatus.NORMAL.withReason("Transmission complete"));
            }
        }
        else {
            log.warn("收到不支持的消息类型，关闭连接");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unsupported message type"));
        }
    }

    // 初始化函数
    private void initProcess(String sessionId) {
        // 创建BlockingQueue
        BlockingQueue<VideoPacket> queue = new LinkedBlockingQueue<>();
        BlockingQueue<VideoPacket> fileQueue = new LinkedBlockingQueue<>();
        // 存入对应的map
        sessionQueueMap.put(sessionId, queue);
        sessionFileQueueMap.put(sessionId, fileQueue);
        // 在线程池中启动sessionId对应的处理线程
        processorPool.submit(() -> processMessage(sessionId, queue, fileQueue));
    }

    // 视频流处理函数
    private void processMessage(String sessionId, BlockingQueue<VideoPacket> queue, BlockingQueue<VideoPacket> fileQueue) {
        log.info("处理线程创建！");
        File outputFile = new File("video_" + sessionId + "_" + System.currentTimeMillis() + ".webm");
        int bufferSize = 2048 * 1024;
        try (PipedOutputStream pipeOut = new PipedOutputStream();
             PipedInputStream pipeIn = new PipedInputStream(pipeOut, bufferSize);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            // 启动解码线程
            Thread videoDecoderThread = new Thread(
                    () -> {
                        videoDecoder(pipeIn, sessionId);
                        decodeDoneLatch.countDown();
                    },
                    "Decoder-" + sessionId
            );
            videoDecoderThread.start();

            // 启动视频保存线程
            Thread videoSaverThread = new Thread(
                    () -> videoSaver(sessionId, fileQueue, fos),
                    "Saver-" + sessionId
            );
            videoSaverThread.start();

            // 不断把 WebSocket 收到的 chunk 写入解码线程将读取的pipeIn管道
            while (true) {
                VideoPacket packet = queue.poll(16, TimeUnit.SECONDS);
                if (packet == null) {
                    log.info("ws视频流处理线程 sessionId={} 超时没有接收到数据", sessionId);
                    pipeOut.close();
                    // 等待解码线程完成读取并退出
                    decodeDoneLatch.await();
                    break;
                }
                else if (packet.isPoisonPill()) {
                    log.info("ws视频流关闭，结束处理线程 sessionId={}", sessionId);
                    pipeOut.close();
                    // 等待解码线程完成读取并退出
                    decodeDoneLatch.await();
                    break;
                }
                else {
                    log.info("packet数据块大小: {}", packet.getData().length);
                    // 导入管道
                    pipeOut.write(packet.getData());
                    pipeOut.flush();

                    // 将数据块放入文件队列
                    fileQueue.offer(packet);
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("处理线程异常 sessionId: {}", sessionId, e);
        }
    }

    public void Interview() {

    }


    // 保存视频流文件函数
    private void videoSaver(String sessionId, BlockingQueue<VideoPacket> fileQueue, FileOutputStream fos) {
        log.info("保存线程创建！");
        try {
            while (true) {
                VideoPacket chunk = fileQueue.poll(16, TimeUnit.SECONDS);
                if (chunk == null) {
                    log.info("视频保存线程 sessionId={} 超时没有接收到数据，结束保存", sessionId);
                    break;
                }
                else if (chunk.isPoisonPill()) {
                    log.info("ws视频流关闭，视频文件写入完成。结束视频流保存线程 sessionId={}", sessionId);
                    break;
                }
                else {
                    // 写入文件
                    fos.write(chunk.getData());
                }
            }
        }
        catch (Exception e) {
            log.error("保存视频流异常 sessionId: {}", sessionId, e);
        } finally {
            try {
                fos.flush();
                fos.close();
            } catch (IOException e) {
                log.error("关闭文件输出流异常 sessionId: {}", sessionId, e);
            }
        }
    }

    // 视频流解码函数
    private void videoDecoder(PipedInputStream pipeIn, String sessionId) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(pipeIn)) {
            grabber.setFormat("webm");
            grabber.setOption("probesize", "2000000");
            grabber.setOption("analyzeduration", "2000000");
            grabber.start();

            // 初始化录音器，设置参数与抓取器一致
            String audioOutputPath = "audio_" + sessionId + "_" + System.currentTimeMillis() + ".wav";
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(audioOutputPath, 1);
            recorder.setFormat("wav");            // 输出wav文件
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setAudioChannels(grabber.getAudioChannels());
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_PCM_S16LE);  // PCM编码，wav常用
            recorder.start();

            Frame frame;
            int frameCount = 0;
            // 循环提取视频帧frame
            while ((frame = grabber.grab()) != null) {
//                if (frame.image != null) {
//                    log.info("解码线程: {} 处理第 {} 帧, 时间戳: {}", sessionId, frameCount++, frame.timestamp);
//                }

                if (frame.samples != null) {
                    recorder.recordSamples(frame.samples);
                    log.info("解码线程: {} 处理第 {} 帧的音频数据, 时间戳: {}", sessionId, frameCount++, frame.timestamp);
                }
            }

            recorder.stop();
            recorder.release();

            grabber.stop();
        } catch (Exception e) {
            log.error("解码线程异常 sessionId: {}", sessionId, e);
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
        sessionQueueMap.remove(sessionId);
        sessionFileQueueMap.remove(sessionId);
        log.info("清理 sessionId={} 的队列和资源", sessionId);
    }


}
