package com.mlab.askvistax.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlab.askvistax.pojo.Interview;
import com.mlab.askvistax.pojo.Message;
import com.mlab.askvistax.pojo.TTSConfig;
import com.mlab.askvistax.service.InterviewService;
import com.mlab.askvistax.utils.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.Block;
import net.sf.jsqlparser.statement.select.Join;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.opencv.video.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import javax.swing.text.View;
import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

// TODO 实现心跳检测超时断连等意外情况
// TODO 构建处理视频流线程，将其放于线程池统一处理
@Component
@Slf4j
public class VideoStreamHandler extends AbstractWebSocketHandler {
    // 保存每个session连接对应的二进制数据缓冲区队列，ConcurrentHashMap线程安全的哈希表容器
    // 每个processMessage线程对应的BlockingQueue
    private final Map<String, BlockingQueue<VideoPacket>> sessionQueueMap = new ConcurrentHashMap<>();
    // 每个videoSaver线程对应的BlockingQueue
    private final Map<String, BlockingQueue<VideoPacket>> sessionFileQueueMap = new ConcurrentHashMap<>();
    // 每个sstHandler线程对应的BlockingQueue
    private final Map<String, BlockingQueue<Frame>> sessionAudioQueueMap = new ConcurrentHashMap<>();

    private final ExecutorService processorPool = Executors.newCachedThreadPool();
    private final ExecutorService aiInterviewPool = Executors.newCachedThreadPool();

    @Autowired
    private InterviewService interviewService;
    @Autowired
    private HttpClientUtil httpClientUtil;
    @Autowired
    private AudioUtil audioUtil;


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        URI uri = session.getUri();
        Map<String, Object> attributes = session.getAttributes();
        Map<String, Object> claims = (Map<String, Object>) attributes.get("claims");
        log.info("session连接: {}建立成功, URI: {}, connect_userAccount: {}, connect_userName: {}, connect_roleType: {}", sessionId, uri, claims.get("userAccount"), claims.get("userName"), CommonConstants.roleTypeMap.get(claims.get("roleType")));

        // 创建此次面试记录目录
        String baseDir = "reviewRecord";
        File sessionDir = new File(baseDir, sessionId);

        if (!sessionDir.exists()) {
            boolean created = sessionDir.mkdirs();
            if (created) {
                log.info("为sessionId={} 创建目录成功: {}", sessionId, sessionDir.getAbsolutePath());
            } else {
                log.warn("为sessionId={} 创建目录失败: {}", sessionId, sessionDir.getAbsolutePath());
            }
        } else {
            log.info("sessionId={} 目录已存在: {}", sessionId, sessionDir.getAbsolutePath());
        }

        initProcess(sessionId);

        Integer interviewId = (Integer) attributes.get("interviewId");

        // AI面试线程池
//        aiInterviewPool.submit(() -> aiInterview(session, interviewId));
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

    // AI面试线程
    public void aiInterview(WebSocketSession session, Integer interviewId) {
        String sessionId = session.getId();
        String baseDir = "reviewRecord";
        log.info("AI面试线程创建，sessionId: {}, interviewId: {}", sessionId, interviewId);
        List<Message> messageHistory = new ArrayList<>();
        File dir = new File(baseDir, sessionId);

        Interview interview = interviewService.getInterviewById(interviewId);
        ResumeGenerateQuestion resumeGenerateQuestion = new ResumeGenerateQuestion();
        List<String> questionList = null;
        try {
            questionList = resumeGenerateQuestion.generareQuestion(interview.getResumeUrl());
        } catch (Exception e) {
            e.getStackTrace();
        }

        // 1. 创建AI面试智能体
        Message createAgentMessage = new Message("system", "你是AI面试官，你将依据通过应聘者简历生成的问题对于应聘者进行一场综合高质量的面试");
        messageHistory.add(createAgentMessage);
        String createJsonBody = null;
        ObjectMapper mapper = new ObjectMapper();
        try{
            createJsonBody = mapper.writeValueAsString(createAgentMessage);
        } catch(Exception e) {
            e.getStackTrace();
        }

        String response = httpClientUtil.postJson(CommonConstants.createAgentUrl, createJsonBody, null);

        // 向前端发送题目及对应的音频
        for (int i = 0; i < questionList.size(); i++) {
            String question = questionList.get(i);
            try {
                TextMessage message = new TextMessage(question);
                session.sendMessage(message);
                TTSConfig ttsConfig = new TTSConfig(question, "x5_lingfeiyi_flow", "mp3");
                String ttsJsonBody = mapper.writeValueAsString(ttsConfig);
                byte[] mp3Bytes = httpClientUtil.postJsonForFile(CommonConstants.ttsUrl, ttsJsonBody, null);
                File mp3File = new File(dir, "Question_" + i + ".mp3");
                try (FileOutputStream fos = new FileOutputStream(mp3File)) {
                    fos.write(mp3Bytes);
                    session.sendMessage(new BinaryMessage(mp3Bytes));
                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                log.error("异常", e);
            }

        }



    }


    // 初始化函数
    private void initProcess(String sessionId) {
        // 创建BlockingQueue
        BlockingQueue<VideoPacket> queue = new LinkedBlockingQueue<>();
        BlockingQueue<VideoPacket> fileQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Frame> audioQueue = new LinkedBlockingQueue<>();
        // 存入对应的map
        sessionQueueMap.put(sessionId, queue);
        sessionFileQueueMap.put(sessionId, fileQueue);
        sessionAudioQueueMap.put(sessionId, audioQueue);
        // 在线程池中启动sessionId对应的处理线程
        processorPool.submit(() -> processMessage(sessionId, queue, fileQueue, audioQueue));
    }

    // 视频流处理函数
    private void processMessage(String sessionId, BlockingQueue<VideoPacket> queue, BlockingQueue<VideoPacket> fileQueue,
                                BlockingQueue<Frame> audioQueue) {
        log.info("处理线程创建！");
        File outputFile = new File("video_" + sessionId + "_" + System.currentTimeMillis() + ".webm");
        int bufferSize = 2048 * 1024;
        try (PipedOutputStream pipeOut = new PipedOutputStream();
             PipedInputStream pipeIn = new PipedInputStream(pipeOut, bufferSize);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            // 启动解码线程
            Thread videoDecoderThread = new Thread(
                    () -> videoDecoder(pipeIn, sessionId, audioQueue),
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
                    videoSaverThread.join();
                    videoDecoderThread.join();
                    break;
                }
                else if (packet.isPoisonPill()) {
                    log.info("ws视频流关闭，结束处理线程 sessionId={}", sessionId);
                    pipeOut.close();
                    videoSaverThread.join();
                    videoDecoderThread.join();
                    break;
                }
                else {
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

    // 语音转文本线程
    private void sttHandler(String sessionId, BlockingQueue<Frame> audioQueue, int sampleRate, int channels) {
        log.info("语音转文本线程启动，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
        List<Frame> frameBuffer = new ArrayList<>();
        long chunkStartTimestamp = -1; // 记录第一个音频帧的时间戳，单位微秒

        try {
            while (true) {
                Frame audioFrame = audioQueue.poll(16, TimeUnit.SECONDS);
                if (audioFrame == null) {
                    log.info("sstHandler线程超时无音频数据，结束，sessionId={}", sessionId);
                    break;
                }
                else if (audioFrame == CommonConstants.POISON_PILL) {
                    log.info("sstHandler线程收到结束信号，结束，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
                    break;
                }

                frameBuffer.add(audioFrame.clone()); // 保存副本

                // 第一次记录时间戳
                if (chunkStartTimestamp < 0) {
                    chunkStartTimestamp = audioFrame.timestamp;
                }

                // 判断是否已累计超过3秒 (3,000,000 微秒)
                long durationMicros = audioFrame.timestamp - chunkStartTimestamp;
                if (durationMicros >= 3_000_000) {
                    log.info("sstHandler线程处理3秒音频，sessionId={}, durationMicros={}, time: {}", sessionId, durationMicros, System.currentTimeMillis());
                    speechToText(frameBuffer, sampleRate, channels);
                    frameBuffer.clear();
                    chunkStartTimestamp = -1;
                }
            }

            if (!frameBuffer.isEmpty()) {
                log.info("sstHandler线程处理剩余音频，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
                speechToText(frameBuffer, sampleRate, channels);
            }
        } catch (InterruptedException e) {
            log.error("sstHandler线程被中断，sessionId={}", sessionId, e);
        }
        log.info("语音转文本线程结束");

    }

    // 语音转文本调用接口
    private void speechToText(List<Frame> audioFrames, int sampleRate, int channels) {
        try {
            // 将多个 Frame 合并成一个 wav 文件
            File audioFile = audioUtil.convertFramesToFile(audioFrames, sampleRate, channels);

            Map<String, String> headers = new HashMap<>();
            Map<String, String> formFields = new HashMap<>();

            JsonNode jsonResponse = httpClientUtil.postAudioFile(CommonConstants.sttUrl, audioFile, headers, formFields);

            if (jsonResponse != null) {
                String text = jsonResponse.path("data").path("full_text").asText();
                System.out.println("识别结果：" + text);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        }
    }

    // 视频流解码函数
    private void videoDecoder(PipedInputStream pipeIn, String sessionId, BlockingQueue<Frame> audioQueue) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(pipeIn)) {
            // 设置抓取器参数
            grabber.setFormat("webm");
            grabber.setOption("probesize", "2000000");
            grabber.setOption("analyzeduration", "2000000");
            grabber.start();

            int sampleRate = grabber.getSampleRate();
            int audioChannels = grabber.getAudioChannels();

            // 启动语音转文本线程sstHandler
            Thread sttHandlerThread = new Thread(
                    () -> sttHandler(sessionId, audioQueue, sampleRate, audioChannels),
                    "sttHandler-" + sessionId
            );
            sttHandlerThread.start();

            // 初始化录音器，设置参数与抓取器一致
            String audioOutputPath = "audio_" + sessionId + "_" + System.currentTimeMillis() + ".wav";
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(audioOutputPath, 1);
            recorder.setFormat("wav");            // 输出wav文件
            recorder.setSampleRate(sampleRate);
            recorder.setAudioChannels(audioChannels);
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
//                    log.info("解码线程: {} 处理第 {} 帧, 时间戳: {}", sessionId, frameCount++, frame.timestamp);
                    recorder.recordSamples(frame.samples);
                    audioQueue.offer(audioUtil.cloneAudioFrame(frame));
                }
            }

            recorder.stop();
            recorder.release();
            grabber.stop();
            audioQueue.offer(CommonConstants.POISON_PILL); // 发送结束信号给语音转文本线程
            sttHandlerThread.join();
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
        sessionAudioQueueMap.remove(sessionId);
        log.info("清理 sessionId={} 的队列和资源", sessionId);
    }


}
