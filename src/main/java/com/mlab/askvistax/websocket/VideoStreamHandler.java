package com.mlab.askvistax.websocket;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlab.askvistax.pojo.*;
import com.mlab.askvistax.service.InterviewService;
import com.mlab.askvistax.utils.*;
import com.sun.jdi.VoidValue;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.Block;
import net.sf.jsqlparser.statement.select.Join;
import org.apache.http.conn.ssl.PrivateKeyDetails;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.presets.opencv_core;
import org.opencv.video.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.*;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import javax.net.ssl.SSLServerSocket;
import javax.swing.text.View;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

// TODO 实现心跳检测超时断连等意外情况
// TODO 构建处理视频流线程，将其放于线程池统一处理
@Component
@Slf4j
public class VideoStreamHandler extends AbstractWebSocketHandler {
    // 保存每个session连接对应的二进制数据缓冲区队列，ConcurrentHashMap线程安全的哈希表容器
    // 每个processMessage线程对应的BlockingQueue
    private final ConcurrentHashMap<String, BlockingQueue<VideoPacket>> sessionQueueMap = new ConcurrentHashMap<>();
    // 每个videoSaver线程对应的BlockingQueue
    private final ConcurrentHashMap<String, BlockingQueue<VideoPacket>> sessionFileQueueMap = new ConcurrentHashMap<>();
    // 每个sstHandler线程对应的BlockingQueue
    private final ConcurrentHashMap<String, BlockingQueue<Frame>> sessionAudioQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<String>> sessionNextSignalQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<String>> sessionSttResultQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<String>> sessionAnswerQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> sessionInterviewIdMap = new ConcurrentHashMap<>();

    // 每个session连接线程对应的processMessage Future
    private final ConcurrentHashMap<String, Future<?>> sessionProcessMessageFutureMap = new ConcurrentHashMap<>();
    // 每个session连接线程对应的ConcurrentWebSocketSession
    private final ConcurrentHashMap<String, WebSocketSession> concurrentWebSocketSessionMap = new ConcurrentHashMap<>();

    private final ExecutorService processorPool = Executors.newCachedThreadPool();
    private final ExecutorService aiInterviewPool = Executors.newCachedThreadPool();

    @Autowired
    private InterviewService interviewService;
    @Autowired
    private HttpClientUtil httpClientUtil;
    @Autowired
    private AudioUtil audioUtil;
    @Autowired
    private RTASRTest rtasrTest;
    @Autowired
    private HuaWeiOBSUtils huaWeiOBSUtils;



    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("这是视频ws");

        String sessionId = session.getId();
        URI uri = session.getUri();
        Map<String, Object> attributes = session.getAttributes();
        Map<String, Object> claims = (Map<String, Object>) attributes.get("claims");
        log.info("session连接: {}建立成功, URI: {}, connect_userAccount: {}, connect_userName: {}, connect_roleType: {}", sessionId, uri, claims.get("userAccount"), claims.get("userName"), CommonConstants.roleTypeMap.get(claims.get("roleType")));

        Integer interviewId = (Integer) attributes.get("interviewId");

        initProcess(session, interviewId);


//        BlockingQueue<String> nextSignalQueue = sessionNextSignalQueueMap.get(sessionId);

        // AI面试线程池
//        aiInterviewPool.submit(() -> aiInterview(sessionId, interviewId, nextSignalQueue));


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
                Future<?> future = sessionProcessMessageFutureMap.get(sessionId);

                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {

                    log.error("等待处理线程结束时异常", e);
                }

                session.close(CloseStatus.NORMAL.withReason("Transmission complete"));
            }
            else if ("__NEXT__".equals(payload)) {
                BlockingQueue<String> nextSignalQueue = sessionNextSignalQueueMap.get(session.getId());
                nextSignalQueue.offer(payload);
            }
        }
        else {
            log.warn("收到不支持的消息类型，关闭连接");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unsupported message type"));
        }
    }

    // AI面试线程
    public void aiInterview(String sessionId, Integer interviewId, BlockingQueue<String> nextSignalQueue) {
        WebSocketSession conCurrentSession = concurrentWebSocketSessionMap.get(sessionId);
        String baseDir = "reviewRecord";
        log.info("AI面试线程创建，sessionId: {}, interviewId: {}", sessionId, interviewId);

        List<Message> messageHistory = new ArrayList<>();
        File dir = new File(baseDir, sessionId);

        Interview interview = interviewService.getInterviewById(interviewId);
        ResumeGenerateQuestion resumeGenerateQuestion = new ResumeGenerateQuestion();
        List<String> questionList = null;
        // 2. 根据简历url从题库中检索题目
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
            log.info("question: {}", question);
            try {
                JSONObject questionJson = new JSONObject();
                questionJson.put("type", "question");
                questionJson.put("content", question);
                conCurrentSession.sendMessage(new TextMessage(questionJson.toJSONString()));

                TTSConfig ttsConfig = new TTSConfig(question, "x5_lingfeiyi_flow", "mp3");
                String ttsJsonBody = mapper.writeValueAsString(ttsConfig);
                byte[] mp3Bytes = httpClientUtil.postJsonForFile(CommonConstants.ttsUrl, ttsJsonBody, null);
                File mp3File = new File(dir, "Question_" + i + ".mp3");
                try (FileOutputStream fos = new FileOutputStream(mp3File)) {
                    fos.write(mp3Bytes);
                    conCurrentSession.sendMessage(new BinaryMessage(mp3Bytes));

                }
                // 阻塞，等待前端发送操作码__NEXT__
                String signal = nextSignalQueue.take();
                if (!"__NEXT__".equals(signal)) {
                    log.warn("等待用户操作码__NEXT__异常，sessionId={}", sessionId);
                    break;
                }
                log.info("收到了__NEXT__操作码");

                // 向sttResultQueue队列发送当前__NEXT__信号
                BlockingQueue<String> sttResultQueue = sessionSttResultQueueMap.get(sessionId);
                sttResultQueue.offer(CommonConstants.STR_NEXT);

                // 阻塞，等待sttRealTimeReceiver线程发送此时回答的语音识别结果
                BlockingQueue<String> answerQueue = sessionAnswerQueueMap.get(sessionId);
                String answer = answerQueue.take();
                log.info("收到sttRealTimeReceiver线程发送的完整回答: {}", answer);


            } catch (Exception e) {
                log.error("异常", e);
            }

        }
    }

    // 初始化函数
    private void initProcess(WebSocketSession session, Integer interviewId) {
        String sessionId = session.getId();
        // 创建BlockingQueue
        BlockingQueue<VideoPacket> queue = new LinkedBlockingQueue<>();
        BlockingQueue<VideoPacket> fileQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Frame> audioQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> sttResultQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> nextSignalQueue = new ArrayBlockingQueue<>(1);
        BlockingQueue<String> answerQueue = new ArrayBlockingQueue<>(1);

        // 包装成线程安全的 session
        WebSocketSession conCurrentSession = new ConcurrentWebSocketSessionDecorator(
                session,
                60 * 1000,  // 发送超时
                10240 * 1024    // 缓冲区大小
        );

        // 存入对应的map
        sessionQueueMap.put(sessionId, queue);
        sessionFileQueueMap.put(sessionId, fileQueue);
        sessionAudioQueueMap.put(sessionId, audioQueue);
        sessionSttResultQueueMap.put(sessionId, sttResultQueue);
        sessionNextSignalQueueMap.put(sessionId, nextSignalQueue);
        sessionAnswerQueueMap.put(sessionId, answerQueue);
        concurrentWebSocketSessionMap.put(sessionId, conCurrentSession);
        sessionInterviewIdMap.put(sessionId, interviewId);

        // 在线程池中启动sessionId对应的处理线程，并将Future存入sessionProcessMessageFutureMap
        Future<?> future = processorPool.submit(() -> processMessage(sessionId, queue, fileQueue));
        sessionProcessMessageFutureMap.put(sessionId, future);
    }

    // 视频流处理函数
    private void processMessage(String sessionId, BlockingQueue<VideoPacket> queue, BlockingQueue<VideoPacket> fileQueue) {
        log.info("处理线程创建！sessionId: {}", sessionId);
        File outputFile = new File("video_" + sessionId + "_" + System.currentTimeMillis() + ".webm");
        int bufferSize = 2048 * 1024;

        try (PipedOutputStream pipeOut = new PipedOutputStream();
             PipedInputStream pipeIn = new PipedInputStream(pipeOut, bufferSize);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            // 启动音频保存线程
            Thread videoDecoderThread = new Thread(
                    () -> videoDecoder(pipeIn, sessionId),
                    "Decoder-" + sessionId
            );
            videoDecoderThread.start();

            // 启动视频保存线程
            Thread videoSaverThread = new Thread(
                    () -> videoSaver(sessionId, fileQueue, fos, outputFile),
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

    // 实时语音转文本结果接收线程
    private void sttRealTimeReceiver(String sessionId) {
        log.info("实时语音转文本接收线程启动，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
        BlockingQueue<String> answerQueue = sessionAnswerQueueMap.get(sessionId);

        // 获取stt语音转写结果队列
        BlockingQueue<String> sttResultQueue = sessionSttResultQueueMap.get(sessionId);
        StringBuffer answerBuffer = new StringBuffer();
        try {
            while (true) {
                String text = sttResultQueue.poll(16, TimeUnit.SECONDS);
                if (text == null) {
                    log.info("sttRealTimeReceiver线程超时无音频数据，结束，sessionId={}", sessionId);
                    log.info("面试中止，此时题目答案: {}", answerBuffer.toString());
                    answerQueue.offer(answerBuffer.toString());
                    break;
                } else if (text.equals(CommonConstants.STR_NEXT)) {
                    log.info("sttRealTimeReceiver线程收到下一题信号，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
                    log.info("此时题目答案: {}", answerBuffer.toString());
                    answerQueue.offer(answerBuffer.toString());
                    answerBuffer.setLength(0);  // 清空答案缓冲区
                    continue;
                } else if (text.equals(CommonConstants.STR_POISON_PILL)) {
                    log.info("sttRealTimeReceiver线程收到结束信号，结束，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
                    break;
                }

                log.info("接收到语音转文本最终结果: {}", text);
                answerBuffer.append(text);

                // 如果 WebSocket 还开着，发送消息
//                if (conCurrentSession.isOpen()) {
//                    JSONObject msgJson = new JSONObject();
//                    msgJson.put("type", "transcript");
//                    msgJson.put("content", text);
//                    log.info("准备发送到客户端: {}", msgJson.toJSONString());
//                    conCurrentSession.sendMessage(new TextMessage(msgJson.toJSONString()));
//
//                    log.info("发送到客户端成功: {}", msgJson.toJSONString());
//                }
//                else {
//                    log.warn("WebSocket 已关闭，无法发送数据: {}", text);
//                }
            }
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage(), e);
        }
        System.out.println("((((((((((((((((");
        System.out.println(answerBuffer.toString());
        System.out.println("((((((((((((((((");
    }

    // 实时语音转文本线程
    private void sttRealTimeTranser(String sessionId, BlockingQueue<Frame> audioQueue) {
        log.info("实时语音转文本线程启动，sessionId={}, time: {}", sessionId, System.currentTimeMillis());

        ByteArrayOutputStream sendBuf = new ByteArrayOutputStream();
        // 获取sst语音转写结果队列
        BlockingQueue<String> sttResultQueue = sessionSttResultQueueMap.get(sessionId);
        try {
            rtasrTest.start(sttResultQueue);
        } catch (Exception e) {
            log.error("连接xfyun websocket链接失败");
        }

        try {
            while (true) {
                Frame audioFrame = audioQueue.poll(16, TimeUnit.SECONDS);
                if (audioFrame == null) {
                    log.info("sstRealTimeTranser线程超时无音频数据，结束，sessionId={}", sessionId);
                    break;
                }
                else if (audioFrame == CommonConstants.POISON_PILL) {
                    log.info("sstRealTimeTranser线程收到结束信号，结束，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
                    break;
                }

                byte[] pcmData = audioUtil.frameToPCM(audioFrame);
                sendBuf.write(pcmData);
                // 每 1280 字节（40ms）发一次
                while (sendBuf.size() >= CommonConstants.FRAME_BYTES) {
                    byte[] all = sendBuf.toByteArray();
                    byte[] toSend = Arrays.copyOfRange(all, 0, CommonConstants.FRAME_BYTES);
                    log.info("发送数据给rtasr线程处理");
                    rtasrTest.sendPCMData(toSend);

                    // 移除已发送部分
                    sendBuf.reset();
                    if (all.length > CommonConstants.FRAME_BYTES) {
                        sendBuf.write(all, CommonConstants.FRAME_BYTES, all.length - CommonConstants.FRAME_BYTES);
                    }
                }
            }
            // 发送剩余（如果有）
            if (sendBuf.size() > 0) {
                rtasrTest.sendPCMData(sendBuf.toByteArray());
            }
        } catch (Exception e) {
            log.error("sstRealTimeTranser线程被中断，sessionId={}", sessionId, e);
        }
        rtasrTest.shutdown();
        log.info("实时语音转文本线程结束");
    }



    // 保存视频流文件函数
    private void videoSaver(String sessionId, BlockingQueue<VideoPacket> fileQueue, FileOutputStream fos,
                            File videoSaveFile) {
        log.info("vsWebsocket视频保存线程创建！");
        ObjectMapper mapper = new ObjectMapper();

        try {
            while (true) {
                VideoPacket chunk = fileQueue.poll(16, TimeUnit.SECONDS);
                if (chunk == null) {
                    log.info("vsWebsocket视频保存线程 sessionId={} 超时没有接收到数据，结束保存", sessionId);
                    break;
                }
                else if (chunk.isPoisonPill()) {
                    log.info("vsWebsocket视频流关闭，视频文件写入完成。结束视频流保存线程 sessionId={}", sessionId);
                    break;
                }
                else {
                    // 写入文件
                    fos.write(chunk.getData());
                }
            }
        }
        catch (Exception e) {
            log.error("vsWebsocket保存视频流异常 sessionId: {}", sessionId, e);
        }

        // 请求视频分析接口
        if (videoSaveFile != null && videoSaveFile.exists()) {
            try {
                Map<String, File> videoFileMap = new HashMap<>();
                videoFileMap.put("video", videoSaveFile); // form-data 字段名是 "video"

                JsonNode videoAnalyzeResponse = httpClientUtil.postMultipartFiles(
                        CommonConstants.videoAnalyzeUrl,
                        videoFileMap,
                        null,
                        null
                );

                VideoAnalyze videoAnalyze = new VideoAnalyze();
                videoAnalyze.setSummary(videoAnalyzeResponse.path("data").path("summary").asText());
                JsonNode global_assessmentNode = videoAnalyzeResponse.path("data").path("global_assessment");
                VideoAssessment videoAssessment = mapper.treeToValue(global_assessmentNode, VideoAssessment.class);
                videoAnalyze.setVideoAssessment(videoAssessment);
                log.info("视频分析完成，分析结果：{}", videoAnalyze);
                Integer interviewId = sessionInterviewIdMap.get(sessionId);
                Interview interview = interviewService.getInterviewById(interviewId);
                interview.setVideoAnalyzeResult(videoAnalyze);

                File videoFile = videoSaveFile;
                MultipartFile multipartFile = null;
                try (FileInputStream fis = new FileInputStream(videoFile)) {
                    multipartFile = new MockMultipartFile(
                            videoFile.getName(),       // 文件名
                            videoFile.getName(),       // originalFilename
                            "video/webm",              // 文件类型，根据实际文件类型填写
                            fis                        // 文件内容流
                    );
                } catch (Exception e) {
                    log.error("File 转 MultipartFile 失败", e);
                }
                log.info("面试视频记录上传至华为云obs成功");
                String videoUrl = huaWeiOBSUtils.upload(multipartFile);
                interview.setVideoUrl(videoUrl);
                interviewService.updateInterview(interview);
                log.info("成功将视频分析结果存入数据库");
            } catch (Exception e) {
                log.error("视频上传失败 sessionId: {}", sessionId, e);
            }
        } else {
            log.warn("视频文件不存在，无法上传 sessionId={}", sessionId);
        }
    }

    // 视频流解码函数
    private void videoDecoder(PipedInputStream pipeIn, String sessionId) {
        log.info("vsSocket音频保存分析线程创建！");
        String audioOutputPath = "audio_" + sessionId + "_" + System.currentTimeMillis() + ".wav";
        File audioFile = new File(audioOutputPath);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(pipeIn)) {
            // 设置抓取器参数
            grabber.setFormat("webm");
            grabber.setOption("probesize", "32"); // 几十字节就开始解析
            grabber.setOption("analyzeduration", "2000"); // 不额外等待分析

            grabber.start();

            int sampleRate = grabber.getSampleRate();
            int audioChannels = grabber.getAudioChannels();

            // 初始化录音器，设置参数与抓取器一致

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
//                    log.info("解码线程: {} 处理第 {} 音频帧, 时间戳: {}", sessionId, frameCount++, frame.timestamp);
                    recorder.recordSamples(frame.samples);
//                    audioQueue.offer(audioUtil.cloneAudioFrame(frame));
                }
            }

            recorder.stop();
            recorder.release();
            grabber.stop();

        } catch (Exception e) {
            log.error("解码线程异常 sessionId: {}", sessionId, e);
        }

        log.info("音频文件成功保存，文件名: {}", audioOutputPath);

        if (audioFile != null && audioFile.exists()) {
            try {
                Map<String, File> fileMap = new HashMap<>();
                fileMap.put("audio", audioFile); // form-data 字段名是 "audio"

                // 串行执行上传，方法返回后才继续执行
                JsonNode audioResponseNode = httpClientUtil.postMultipartFiles(
                        CommonConstants.audioAnalyzeuUrl,
                        fileMap,
                        null,       // 额外请求头
                        null  // 额外表单字段
                );

                AudioAnalyze audioAnalyze = new AudioAnalyze();
                audioAnalyze.setTone(audioResponseNode.path("data").path("tone").asText());
                audioAnalyze.setPitchVariation(audioResponseNode.path("data").path("pitch_variation").asText());
                audioAnalyze.setSpeechRate(audioResponseNode.path("data").path("speech_rate").asText());
                audioAnalyze.setSummary(audioResponseNode.path("data").path("summary").asText());

                log.info("音频分析完成，分析结果：{}", audioAnalyze);

                Integer interviewId = sessionInterviewIdMap.get(sessionId);
                Interview interview = interviewService.getInterviewById(interviewId);
                interview.setAudioAnalyzeResult(audioAnalyze);
                interviewService.updateInterview(interview);
                log.info("将音频分析结果存入数据库");

            } catch (Exception e) {
                log.error("音频上传或分析失败 sessionId: {}", sessionId, e);
            }
        } else {
            log.warn("音频文件不存在，无法上传分析 sessionId={}", sessionId);
        }



        log.info("vsSocket音频保存分析线程退出！");
    }

    // TODO 弃用的一些实现
    // 语音转文本线程
//    private void sttHandler(String sessionId, BlockingQueue<Frame> audioQueue, int sampleRate, int channels) {
//        log.info("语音转文本线程启动，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
//        List<Frame> frameBuffer = new ArrayList<>();
//        long chunkStartTimestamp = -1; // 记录第一个音频帧的时间戳，单位微秒
//        long lastVoiceTimestamp = -1;   // 最近一个有声帧时间戳，单位微秒
//        final long silenceThresholdMicros = 1_000_000;  // 1秒静音阈值，单位微秒
//        final long defaultChunkDurationMicros = 5_000_000; // 默认5秒切分，单位微秒
//
//        try {
//            while (true) {
//                Frame audioFrame = audioQueue.poll(16, TimeUnit.SECONDS);
//                if (audioFrame == null) {
//                    log.info("sstHandler线程超时无音频数据，结束，sessionId={}", sessionId);
//                    break;
//                }
//                else if (audioFrame == CommonConstants.POISON_PILL) {
//                    log.info("sstHandler线程收到结束信号，结束，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
//                    break;
//                }
//
//                if (chunkStartTimestamp < 0) {
//                    chunkStartTimestamp = audioFrame.timestamp;
//                    lastVoiceTimestamp = audioFrame.timestamp;
//                }
//
//                frameBuffer.add(audioFrame.clone());
//
//                boolean isSilent = audioUtil.isSilentFrame(audioFrame);
//                if (!isSilent) {    // 非静音数据更新最近有声时间戳
//                    lastVoiceTimestamp = audioFrame.timestamp;
//                }
//
//                // 判断是否累计静音超过阈值
//                long silenceDuration = audioFrame.timestamp - lastVoiceTimestamp;
//                if (silenceDuration >= silenceThresholdMicros && !frameBuffer.isEmpty()) {
//                    // 超过静音阈值，切分并处理之前的音频块
//                    log.info("sstHandler检测到静音超过阈值，处理音频，sessionId={}, silenceDurationMicros={}", sessionId, silenceDuration);
//                    if (audioUtil.hasMeaningfulAudioEnergy(frameBuffer)) {
//                        List<Frame> toSTTProcess = audioUtil.cloneFrameBuffer(frameBuffer);
//                        speechToTextPool.submit(() -> speechToText(toSTTProcess, sampleRate, channels));
//                    } else {
//                        log.info("sstHandler检测到静音，缓冲区无有效音频，跳过语音识别，sessionId={}", sessionId);
//                    }
//                    frameBuffer.clear();
//                    chunkStartTimestamp = -1;
//                    lastVoiceTimestamp = -1;
//                }
//                else {
//                    long durationMicros = audioFrame.timestamp - chunkStartTimestamp;
//                    if (durationMicros >= defaultChunkDurationMicros) { // 若大于等于默认10秒
//                        log.info("sstHandler线程处理5秒音频，sessionId={}, durationMicros={}", sessionId, durationMicros);
//                        List<Frame> toSTTProcess = audioUtil.cloneFrameBuffer(frameBuffer);
//                        speechToTextPool.submit(() -> speechToText(toSTTProcess, sampleRate, channels));
//                        frameBuffer.clear();
//                        chunkStartTimestamp = -1;
//                        lastVoiceTimestamp = -1;
//                    }
//                }
//            }
//
//            if (!frameBuffer.isEmpty()) {
//                log.info("sstHandler线程处理剩余音频，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
//                if (audioUtil.hasMeaningfulAudioEnergy(frameBuffer)) {
//                    List<Frame> toSTTProcess = audioUtil.cloneFrameBuffer(frameBuffer);
//                    speechToTextPool.submit(() -> speechToText(toSTTProcess, sampleRate, channels));
//                } else {
//                    log.info("sstHandler检测到静音，缓冲区无有效音频，跳过语音识别，sessionId={}", sessionId);
//                }
//            }
//        } catch (InterruptedException e) {
//            log.error("sstHandler线程被中断，sessionId={}", sessionId, e);
//        }
//        log.info("语音转文本线程结束");
//    }
//
//    // 语音转文本调用接口
//    private void speechToText(List<Frame> audioFrames, int sampleRate, int channels) {
//        try {
//            // 将多个 Frame 合并成一个 wav 文件
//            File audioFile = audioUtil.convertFramesToFile(audioFrames, sampleRate, channels);
//
//            Map<String, String> headers = new HashMap<>();
//            Map<String, String> formFields = new HashMap<>();
//
//            JsonNode jsonResponse = httpClientUtil.postAudioFile(CommonConstants.sttUrl, audioFile, headers, formFields);
//
//            if (jsonResponse != null) {
//                String text = jsonResponse.path("data").path("full_text").asText();
//                log.info("识别结果: {}", text);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }



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
        sessionNextSignalQueueMap.remove(sessionId);
        sessionSttResultQueueMap.remove(sessionId);
        sessionProcessMessageFutureMap.remove(sessionId);
        concurrentWebSocketSessionMap.remove(sessionId);
        sessionAnswerQueueMap.remove(sessionId);

        log.info("清理 sessionId={} 的队列和资源", sessionId);
    }
}
