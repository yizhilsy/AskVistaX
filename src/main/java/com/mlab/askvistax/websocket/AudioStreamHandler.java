package com.mlab.askvistax.websocket;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.stereotype.Component;
import org.springframework.validation.ObjectError;
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

@Component
@Slf4j
public class AudioStreamHandler extends AbstractWebSocketHandler {
    // 保存每个session连接对应的二进制数据缓冲区队列，ConcurrentHashMap线程安全的哈希表容器
    // 每个processMessage线程对应的BlockingQueue
    private final ConcurrentHashMap<String, BlockingQueue<VideoPacket>> sessionQueueMap = new ConcurrentHashMap<>();

    // 每个sstHandler线程对应的BlockingQueue
    private final ConcurrentHashMap<String, BlockingQueue<Frame>> sessionAudioQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<String>> sessionNextSignalQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<String>> sessionSttResultQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<String>> sessionAnswerQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<Message>> sessionAnalyzeQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Message>> sessionMessageHistoryMap = new ConcurrentHashMap<>();

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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("这是音频ws");
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

        initProcess(session);

        Integer interviewId = (Integer) attributes.get("interviewId");
        BlockingQueue<String> nextSignalQueue = sessionNextSignalQueueMap.get(sessionId);

        // AI面试线程池
        aiInterviewPool.submit(() -> aiInterview(sessionId, interviewId, nextSignalQueue));


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
                // 发送结束信号给各个进程
                queue.offer(VideoPacket.poisonPill());

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
        BlockingQueue<String> sttResultQueue = sessionSttResultQueueMap.get(sessionId);
        BlockingQueue<String> answerQueue = sessionAnswerQueueMap.get(sessionId);

        String baseDir = "reviewRecord";

        log.info("AI面试线程创建，sessionId: {}, interviewId: {}", sessionId, interviewId);

        // 取出此时session连接的消息历史列表
        List<Message> messageHistory = sessionMessageHistoryMap.get(sessionId);
        File dir = new File(baseDir, sessionId);

        Interview interview = interviewService.getInterviewById(interviewId);
        ResumeGenerateQuestion resumeGenerateQuestion = new ResumeGenerateQuestion();
        List<String> questionList = null;

        // 0. 根据简历url从题库中检索题目，并解析简历作为prompt
        // 获取检索到的题目
        try {
            questionList = resumeGenerateQuestion.generareQuestion(interview.getResumeUrl());
        } catch (Exception e) {
            e.getStackTrace();
        }
        // 解析简历
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> resumeFormData = new HashMap<>();
        resumeFormData.put("pdf_url", interview.getResumeUrl());
        String resumeResponse  = httpClientUtil.postForm(CommonConstants.resumeUrlConvertUrl, resumeFormData, null);
        String resumeMarkDown = null;
        try {
            JsonNode root = mapper.readTree(resumeResponse);
            resumeMarkDown = root.path("data").path("markdown").asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        String systemPromptTemplate = "你是AI面试官，你将依据通过应聘者简历生成的问题对于应聘者进行一场综合高质量的面试，应聘者的简历信息为：%s，在之后的消息列表中是你和应聘者之间的对话记录。";
        String systemPrompt = String.format(systemPromptTemplate, resumeMarkDown);

        // 1. 创建AI面试智能体
        Message createAgentMessage = new Message("system", systemPrompt);
        // 加入消息历史列表
        messageHistory.add(createAgentMessage);
        String createJsonBody = null;

        try{
            createJsonBody = mapper.writeValueAsString(createAgentMessage);
        } catch(Exception e) {
            e.getStackTrace();
        }

        // 发送创建AI面试智能体请求
        String createAgentResponse = httpClientUtil.postJson(CommonConstants.createAgentUrl, createJsonBody, null);

        // 2. 问候tts生成
        Message greetMessage  = new Message("assistant", "你好，我是面试官，我已经看过你的简历了，如果你准备好了我们可以开始面试");
        TTSConfig greetTTSConfig = new TTSConfig(greetMessage.getContent(), "x5_lingfeiyi_flow", "mp3");
        try {
            String greetTTSJsonBody = mapper.writeValueAsString(greetTTSConfig);
            byte[] greetMp3Bytes = httpClientUtil.postJsonForFile(CommonConstants.ttsUrl, greetTTSJsonBody, null);
            JSONObject greetJson = new JSONObject();
            greetJson.put("type", "question");
            greetJson.put("content", greetMessage.getContent());
            // 发送问候文本及tts音频
            conCurrentSession.sendMessage(new TextMessage(greetJson.toJSONString()));
            conCurrentSession.sendMessage(new BinaryMessage(greetMp3Bytes));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String signal = null;
        String answer = null;

        try {
            // 阻塞，等待前端发送操作码__NEXT__
            signal = nextSignalQueue.take();
            if (!"__NEXT__".equals(signal)) {
                log.warn("等待用户操作码__NEXT__异常，sessionId={}", sessionId);
                return;
            }
            log.info("收到了__NEXT__操作码");
            // 向sttResultQueue队列发送当前__NEXT__信号
            sttResultQueue.offer(CommonConstants.STR_NEXT);
            // 阻塞，等待sttRealTimeReceiver线程发送此时回答的语音识别结果
            answer = answerQueue.take();
        } catch (Exception e) {
            log.error("异常", e);
        }

        Message userGreetAnswerMessage = new Message("user", answer);
        messageHistory.add(greetMessage);
        messageHistory.add(userGreetAnswerMessage);

        // 创建analyzer线程并行处理分析
        Thread analyzerThread = new Thread(
                () -> analyzer(sessionId, interviewId),
                "analyzer-" + sessionId
        );
        analyzerThread.start();

        BlockingQueue<Message> analyzeQueue = sessionAnalyzeQueueMap.get(sessionId);
        // 3. (客观题)向前端发送题目及对应的音频
        for (int i = questionList.size() - 3; i < questionList.size(); i++) {
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
                signal = nextSignalQueue.take();
                if (!"__NEXT__".equals(signal)) {
                    log.warn("等待用户操作码__NEXT__异常，sessionId={}", sessionId);
                    break;
                }
                log.info("收到了__NEXT__操作码");

                // 向sttResultQueue队列发送当前__NEXT__信号
                sttResultQueue.offer(CommonConstants.STR_NEXT);

                // 阻塞，等待sttRealTimeReceiver线程发送此时回答的语音识别结果
                answer = answerQueue.take();

                // 将问题和回答塞入analyzeQueue，由analyzer线程并行追加进消息历史列表并分析
                analyzeQueue.offer(new Message("assistant", question));
                analyzeQueue.offer(new Message("user", answer));

            } catch (Exception e) {
                log.error("异常", e);
            }
        }
        log.info("客观题板块结束");

        // 4. 生成主观题
        Map<String, Object> subjective_question_RequestBodyMap = new HashMap<>();
        subjective_question_RequestBodyMap.put("messages", messageHistory);
        String subjective_question = null;
        String subjective_objective = null;
        try {
            String subjective_question_jsonBody = mapper.writeValueAsString(subjective_question_RequestBodyMap);
            String subjective_question_response = httpClientUtil.postJson(CommonConstants.subjective_questionUrl, subjective_question_jsonBody, null);
            JsonNode subjective_question_response_root = mapper.readTree(subjective_question_response);
            subjective_question = subjective_question_response_root.path("question").asText();
            subjective_objective = subjective_question_response_root.path("objective").asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        log.info("subjective_question: {}, subjective_objective: {}", subjective_question, subjective_objective);

        // 向前端发送主观题及对应的音频
        try {
            TTSConfig subjectiveQuestionTTSConfig = new TTSConfig(subjective_question, "x5_lingfeiyi_flow", "mp3");
            String subjectiveQuestionTTSJsonBody = mapper.writeValueAsString(subjectiveQuestionTTSConfig);
            byte[] subjectiveQuestionMp3Bytes = httpClientUtil.postJsonForFile(CommonConstants.ttsUrl, subjectiveQuestionTTSJsonBody, null);
            JSONObject subjectiveQuestionJson = new JSONObject();
            subjectiveQuestionJson.put("type", "question");
            subjectiveQuestionJson.put("content", subjective_question);
            // 发送主观题文本及tts音频
            conCurrentSession.sendMessage(new TextMessage(subjectiveQuestionJson.toJSONString()));
            conCurrentSession.sendMessage(new BinaryMessage(subjectiveQuestionMp3Bytes));
        } catch (Exception e) {
            log.error("异常", e);
        }

        try {
            // 阻塞，等待前端发送操作码__NEXT__
            signal = nextSignalQueue.take();
            if (!"__NEXT__".equals(signal)) {
                log.warn("等待用户操作码__NEXT__异常，sessionId={}", sessionId);
                return;
            }
            log.info("收到了__NEXT__操作码");
            // 向sttResultQueue队列发送当前__NEXT__信号
            sttResultQueue.offer(CommonConstants.STR_NEXT);
            // 阻塞，等待sttRealTimeReceiver线程发送此时回答的语音识别结果
            answer = answerQueue.take();
        } catch (Exception e) {
            log.error("异常", e);
        }

        // 将主观题文本和回答塞入消息历史列表
        Message subjectiveQuestionMessage = new Message("assistant", subjective_question);
        Message userSubjectiveAnswerMessage = new Message("user", answer);
        messageHistory.add(subjectiveQuestionMessage);
        messageHistory.add(userSubjectiveAnswerMessage);


        boolean needs_followup = false;
        double overall_score = -1;

        // 对于主观题原题的回答调用分析接口
        Map<String, Object> subjectiveEvaluateRequestBodyMap = new HashMap<>();
        subjectiveEvaluateRequestBodyMap.put("messages", messageHistory);
        JsonNode subjectiveOriginEvaluateResponseRoot = null;
        try {
            String evaluateJsonBody = mapper.writeValueAsString(subjectiveEvaluateRequestBodyMap);
            String evaluateResponse = httpClientUtil.postJson(CommonConstants.evaluateUrl, evaluateJsonBody, null);
            subjectiveOriginEvaluateResponseRoot = mapper.readTree(evaluateResponse);

            // 将主观题分析结果放入数据库
            InterviewResult interviewResult = new InterviewResult();
            interviewResult.setQuestion(subjectiveQuestionMessage.getContent());
            interviewResult.setAnswer(userSubjectiveAnswerMessage.getContent());
            interviewResult.setInterviewId(interviewId);
            interviewResult.setOverallScore(subjectiveOriginEvaluateResponseRoot.path("overall_score").asDouble());
            // 获取 dimensions 节点
            JsonNode dimensionsNode = subjectiveOriginEvaluateResponseRoot.path("dimensions");
            // 解析成 Dimensions 对象
            Dimensions dimensions = mapper.treeToValue(dimensionsNode, Dimensions.class);
            // 存入 InterviewResult
            interviewResult.setDimensions(dimensions);
            interviewResult.setFeedback(subjectiveOriginEvaluateResponseRoot.path("feedback").asText());
            interviewResult.setNeedFollowup(subjectiveOriginEvaluateResponseRoot.path("needs_followup").asBoolean());

            interviewService.recordResult(interviewResult);
            overall_score = subjectiveOriginEvaluateResponseRoot.path("overall_score").asInt();
            needs_followup = subjectiveOriginEvaluateResponseRoot.path("needs_followup").asBoolean();
            log.info("主观题原题overall_score: {}, needs_followup: {}", overall_score, needs_followup);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }



        // 对于主观题判断是否需要追问，最大追问次数为3次
        String followUpQuestion = null;
        String followUpAnswer = null;
        JsonNode preEvaluateResponseRoot = subjectiveOriginEvaluateResponseRoot;
        int maxFollowUp = 3;
        int followUp = 1;
        while (followUp <= maxFollowUp && needs_followup && overall_score < 6) {
            // 调用追问接口
            Map<String, Object> followUpRequestBodyMap = new HashMap<>();
            followUpRequestBodyMap.put("messages", messageHistory);
            followUpRequestBodyMap.put("evaluation_result", preEvaluateResponseRoot);

            try {
                String followUpJsonBody = mapper.writeValueAsString(followUpRequestBodyMap);
                String followUpResponse = httpClientUtil.postJson(CommonConstants.followUpUrl, followUpJsonBody, null);
                JsonNode followUpResponseRoot = mapper.readTree(followUpResponse);
                followUpQuestion = followUpResponseRoot.path("question").asText();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            log.info("追问问题: {}", followUpQuestion);

            // 向前端发送追问问题及音频
            try {
                TTSConfig followUpTTSConfig = new TTSConfig(followUpQuestion, "x5_lingfeiyi_flow", "mp3");
                String followUpTTSJsonBody = mapper.writeValueAsString(followUpTTSConfig);
                byte[] followUpMp3Bytes = httpClientUtil.postJsonForFile(CommonConstants.ttsUrl, followUpTTSJsonBody, null);
                JSONObject followUpJson = new JSONObject();
                followUpJson.put("type", "question");
                followUpJson.put("content", followUpQuestion);
                // 发送追问文本及tts音频
                conCurrentSession.sendMessage(new TextMessage(followUpJson.toJSONString()));
                conCurrentSession.sendMessage(new BinaryMessage(followUpMp3Bytes));
            } catch (Exception e) {
                log.error("异常", e);
            }

            try {
                // 阻塞，等待前端发送操作码__NEXT__
                signal = nextSignalQueue.take();
                if (!"__NEXT__".equals(signal)) {
                    log.warn("等待用户操作码__NEXT__异常，sessionId={}", sessionId);
                    return;
                }
                log.info("收到了__NEXT__操作码");
                // 向sttResultQueue队列发送当前__NEXT__信号
                sttResultQueue.offer(CommonConstants.STR_NEXT);
                // 阻塞，等待sttRealTimeReceiver线程发送此时回答的语音识别结果
                answer = answerQueue.take();
            } catch (Exception e) {
                log.error("异常", e);
            }

            // 将追问问题及回答塞入消息历史列表
            followUpAnswer = answer;
            Message followUpMessage = new Message("assistant", followUpQuestion);
            Message userFollowUpAnswerMessage = new Message("user", followUpAnswer);
            messageHistory.add(followUpMessage);
            messageHistory.add(userFollowUpAnswerMessage);

            // 调用evaluate接口评估追问回答的质量
            Map<String, Object> evaluateRequestBodyMap = new HashMap<>();
            evaluateRequestBodyMap.put("messages", messageHistory);
            try {
                String evaluateJsonBody = mapper.writeValueAsString(evaluateRequestBodyMap);
                String evaluateResponse = httpClientUtil.postJson(CommonConstants.evaluateUrl, evaluateJsonBody, null);
                JsonNode evaluateResponseRoot = mapper.readTree(evaluateResponse);

                // 追问分析结果放入数据库
                InterviewResult interviewResult = new InterviewResult();
                interviewResult.setQuestion(followUpQuestion);
                interviewResult.setAnswer(followUpAnswer);
                interviewResult.setInterviewId(interviewId);
                interviewResult.setOverallScore(evaluateResponseRoot.path("overall_score").asDouble());
                // 获取 dimensions 节点
                JsonNode dimensionsNode = evaluateResponseRoot.path("dimensions");
                // 解析成 Dimensions 对象
                Dimensions dimensions = mapper.treeToValue(dimensionsNode, Dimensions.class);
                // 存入 InterviewResult
                interviewResult.setDimensions(dimensions);
                interviewResult.setFeedback(evaluateResponseRoot.path("feedback").asText());
                interviewResult.setNeedFollowup(evaluateResponseRoot.path("needs_followup").asBoolean());

                interviewService.recordResult(interviewResult);

                overall_score = evaluateResponseRoot.path("overall_score").asInt();
                needs_followup = evaluateResponseRoot.path("needs_followup").asBoolean();
                preEvaluateResponseRoot = evaluateResponseRoot;
                log.info("追问回答overall_score: {}, needs_followup: {}", overall_score, needs_followup);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            followUp++;
        }

        // AI 面试结束
        try {
            // 向前端发送面试终止消息
            JSONObject endJson = new JSONObject();
            endJson.put("type", "end");
            conCurrentSession.sendMessage(new TextMessage(endJson.toJSONString()));
            // 向面试分析线程发送分析队列结束信号


        } catch (Exception e) {
            log.error("异常", e);
        }

        log.info("AI 面试线程结束");
        log.info("messageHistory: {}", messageHistory);
    }

    private void analyzer(String sessionId, Integer interviewId) {
        log.info("面试分析线程创建！");
        // 获取分析队列
        BlockingQueue<Message> analyzeQueue = sessionAnalyzeQueueMap.get(sessionId);
        ObjectMapper mapper = new ObjectMapper();
        try {
            while(true) {
                // 一次取出两条消息
                Message assistantMessage = analyzeQueue.take();
                if (assistantMessage == CommonConstants.MSG_POISON_PILL) {
                    log.info("面试分析线程收到结束信号，sessionId: {}", sessionId);
                    break;
                }
                Message userMessage = analyzeQueue.take();
                // 追加进消息历史
                List<Message> messageHistory = sessionMessageHistoryMap.get(sessionId);
                messageHistory.add(assistantMessage);
                messageHistory.add(userMessage);
                // 构造evaluate请求参数并调用evaluate接口获取分析结果
                Map<String, Object> evaluateRequestBodyMap = new HashMap<>();
                evaluateRequestBodyMap.put("messages", messageHistory);
                String evaluateJsonBody = mapper.writeValueAsString(evaluateRequestBodyMap);
                String evaluateResponse = httpClientUtil.postJson(CommonConstants.evaluateUrl, evaluateJsonBody, null);
                JsonNode evaluateResponseRoot = mapper.readTree(evaluateResponse);

                // 将分析结果存入数据库
                InterviewResult interviewResult = new InterviewResult();
                interviewResult.setQuestion(assistantMessage.getContent());
                interviewResult.setAnswer(userMessage.getContent());
                interviewResult.setInterviewId(interviewId);
                interviewResult.setOverallScore(evaluateResponseRoot.path("overall_score").asDouble());
                // 获取 dimensions 节点
                JsonNode dimensionsNode = evaluateResponseRoot.path("dimensions");
                // 解析成 Dimensions 对象
                Dimensions dimensions = mapper.treeToValue(dimensionsNode, Dimensions.class);
                // 存入 InterviewResult
                interviewResult.setDimensions(dimensions);
                interviewResult.setFeedback(evaluateResponseRoot.path("feedback").asText());
                interviewResult.setNeedFollowup(evaluateResponseRoot.path("needs_followup").asBoolean());

                interviewService.recordResult(interviewResult);
                log.info("成功将客观题分析结果存入数据库");
            }
        } catch (Exception e) {
            log.error("分析线程异常", e);
        }
        log.info("面试分析线程退出, sessionId: {}", sessionId);
    }


    // 初始化函数
    private void initProcess(WebSocketSession session) {
        String sessionId = session.getId();
        // 创建BlockingQueue
        BlockingQueue<VideoPacket> queue = new LinkedBlockingQueue<>();
        BlockingQueue<Frame> audioQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> sttResultQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> nextSignalQueue = new ArrayBlockingQueue<>(1);
        BlockingQueue<String> answerQueue = new ArrayBlockingQueue<>(1);
        BlockingQueue<Message> analyzeQueue = new ArrayBlockingQueue<>(2);
        List<Message> messageHistory = new ArrayList<>();

        // 包装成线程安全的 session
        WebSocketSession conCurrentSession = new ConcurrentWebSocketSessionDecorator(
                session,
                60 * 1000,  // 发送超时
                10240 * 1024    // 缓冲区大小
        );

        // 存入对应的map
        sessionQueueMap.put(sessionId, queue);
        sessionAudioQueueMap.put(sessionId, audioQueue);
        sessionSttResultQueueMap.put(sessionId, sttResultQueue);
        sessionNextSignalQueueMap.put(sessionId, nextSignalQueue);
        sessionAnswerQueueMap.put(sessionId, answerQueue);
        sessionAnalyzeQueueMap.put(sessionId, analyzeQueue);
        sessionMessageHistoryMap.put(sessionId, messageHistory);
        concurrentWebSocketSessionMap.put(sessionId, conCurrentSession);

        // 在线程池中启动sessionId对应的处理线程，并将Future存入sessionProcessMessageFutureMap
        Future<?> future = processorPool.submit(() -> processMessage(sessionId));
        sessionProcessMessageFutureMap.put(sessionId, future);
    }

    // 视频流处理函数
    private void processMessage(String sessionId) {
        log.info("处理线程创建！sessionId: {}", sessionId);
        BlockingQueue<VideoPacket> queue = sessionQueueMap.get(sessionId);
        int bufferSize = 2048 * 1024;

        try (PipedOutputStream pipeOut = new PipedOutputStream();
             PipedInputStream pipeIn = new PipedInputStream(pipeOut, bufferSize);) {

            // 启动解码线程
            Thread audioDecoderThread = new Thread(
                    () -> audioDecoder(pipeIn, sessionId),
                    "Decoder-" + sessionId
            );
            audioDecoderThread.start();

            Thread sttRealTimeReceiverThread = new Thread(
                    () -> sttRealTimeReceiver(sessionId),
                    "sttRealTimeReceiver-" + sessionId
            );
            sttRealTimeReceiverThread.start();

            // 不断把 WebSocket 收到的 chunk 写入解码线程将读取的pipeIn管道
            while (true) {
                VideoPacket packet = queue.poll(16, TimeUnit.SECONDS);
                if (packet == null) {
                    log.info("ws视频流处理线程 sessionId={} 超时没有接收到数据", sessionId);
                    pipeOut.close();
                    audioDecoderThread.join();
                    sttRealTimeReceiverThread.join();
                    break;
                }
                else if (packet.isPoisonPill()) {
                    log.info("ws视频流关闭，结束处理线程 sessionId={}", sessionId);
                    pipeOut.close();
                    audioDecoderThread.join();
                    sttRealTimeReceiverThread.join();
                    break;
                }
                else {
                    // 导入管道
                    pipeOut.write(packet.getData());
                    pipeOut.flush();

                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("处理线程异常 sessionId: {}", sessionId, e);
        }
    }

    // 音频流解码函数
    private void audioDecoder(PipedInputStream pipeIn, String sessionId) {
        log.info("audio解码线程创建！");
        ByteArrayOutputStream sendBuf = new ByteArrayOutputStream();
        // 获取stt语音转写结果队列
        BlockingQueue<String> sttResultQueue = sessionSttResultQueueMap.get(sessionId);
        // 启动语音转写ws客户端
        try {
            rtasrTest.start(sttResultQueue);
        } catch (Exception e) {
            log.error("连接xfyun websocket链接失败");
        }


        byte[] buffer = new byte[1024]; // 每次从 pipe 读 1024 bytes，可调
        int read;
        try {
            while ((read = pipeIn.read(buffer)) != -1) {
                // 写入缓冲
                sendBuf.write(buffer, 0, read);

                // 每 FRAME_BYTES 发送一次
                while (sendBuf.size() >= CommonConstants.FRAME_BYTES) {
                    byte[] all = sendBuf.toByteArray();
                    byte[] toSend = Arrays.copyOfRange(all, 0, CommonConstants.FRAME_BYTES);

                    // 调用你的 RTASR 发送方法
//                    log.info("发送数据给rtasr线程处理, sessionId={}, bytes={}", sessionId, toSend.length);
                    rtasrTest.sendPCMData(toSend);

                    // 移除已发送部分
                    sendBuf.reset();
                    if (all.length > CommonConstants.FRAME_BYTES) {
                        sendBuf.write(all, CommonConstants.FRAME_BYTES, all.length - CommonConstants.FRAME_BYTES);
                    }
                }
            }
            // 数据流结束后，如果缓冲还有残余数据也发送
            if (sendBuf.size() > 0) {
                byte[] remaining = sendBuf.toByteArray();
                log.info("发送剩余数据给rtasr线程处理, sessionId={}, bytes={}", sessionId, remaining.length);
                rtasrTest.sendPCMData(remaining);
            }
        } catch (Exception e) {
            log.error("解码线程异常 sessionId: {}", sessionId, e);
        }
        rtasrTest.shutdown();
        log.info("实时语音转文本线程结束");
    }


    // 实时语音转文本结果接收线程
    private void sttRealTimeReceiver(String sessionId) {
        log.info("实时语音转文本接收线程启动，sessionId={}, time: {}", sessionId, System.currentTimeMillis());
        // 获取每题的最终答案队列
        BlockingQueue<String> answerQueue = sessionAnswerQueueMap.get(sessionId);
        WebSocketSession conCurrentSession = concurrentWebSocketSessionMap.get(sessionId);

        // 获取stt语音转写结果队列
        BlockingQueue<String> sttResultQueue = sessionSttResultQueueMap.get(sessionId);
        StringBuffer answerBuffer = new StringBuffer();
        try {
            while (true) {
                String text = sttResultQueue.take();
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

                log.info("接收到一段完整句子的结果: {}", text);
                answerBuffer.append(text);

                // 如果 WebSocket 还开着，发送消息
                if (conCurrentSession.isOpen()) {
                    JSONObject msgJson = new JSONObject();
                    msgJson.put("type", "transcript");
                    msgJson.put("content", text);
                    log.info("准备发送到客户端: {}", msgJson.toJSONString());
                    conCurrentSession.sendMessage(new TextMessage(msgJson.toJSONString()));

                    log.info("发送到客户端成功: {}", msgJson.toJSONString());
                }
                else {
                    log.warn("WebSocket 已关闭，无法发送数据: {}", text);
                }
            }
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage(), e);
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
        sessionAudioQueueMap.remove(sessionId);
        sessionNextSignalQueueMap.remove(sessionId);
        sessionSttResultQueueMap.remove(sessionId);
        sessionProcessMessageFutureMap.remove(sessionId);
        concurrentWebSocketSessionMap.remove(sessionId);
        sessionAnswerQueueMap.remove(sessionId);

        log.info("清理 sessionId={} 的队列和资源", sessionId);
    }
}

