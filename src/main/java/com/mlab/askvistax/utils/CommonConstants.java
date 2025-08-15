package com.mlab.askvistax.utils;

import com.mlab.askvistax.pojo.Message;
import org.bytedeco.javacv.Frame;

import java.util.HashMap;
import java.util.Map;

public class CommonConstants {
    public static Map<Integer, String> roleTypeMap = new HashMap<>();
    public static String resumeQuestionUrl;
    public static String createAgentUrl;
    public static String ttsUrl;
    public static String sttUrl;
    public static String resumeUrlConvertUrl;
    public static String subjective_questionUrl;
    public static String evaluateUrl;
    public static String followUpUrl;
    public static String videoAnalyzeUrl;
    public static String audioAnalyzeuUrl;
    public static String summaryUrl;

    // 定义一个单例结束标记 Frame 对象
    public static final Frame POISON_PILL = new Frame();

    public static int FRAME_MS;
    public static int TARGET_SR;
    public static int FRAME_SAMPLES;
    public static int FRAME_BYTES;

    public static final String STR_POISON_PILL;

    public static final String STR_NEXT;
    public static final Message MSG_POISON_PILL = new Message();

    static {
        roleTypeMap.put(0, "Admin");
        roleTypeMap.put(1, "Interviewer");
        roleTypeMap.put(2, "Candidate");
        resumeQuestionUrl = "http://58.199.161.182:8000/utils/questions/upload-resume-file";
        createAgentUrl = "http://58.199.161.182:8000/llm/message/create";
        ttsUrl = "http://58.199.161.182:8000/audio/tts/synthesize";
        sttUrl = "http://58.199.161.182:8000/audio/upload";
        resumeUrlConvertUrl = "http://58.199.161.182:8000/utils/convert-url";
        subjective_questionUrl = "http://58.199.161.182:8000/llm/subjective_question";
        evaluateUrl = "http://58.199.161.182:8000/llm/evaluate";
        followUpUrl = "http://58.199.161.182:8000/llm/followup";
        videoAnalyzeUrl = "http://58.199.161.182:8000/understanding/video/analyze";
        audioAnalyzeuUrl = "http://58.199.161.182:8000/understanding/audio/analyze";
        summaryUrl = "http://58.199.161.182:8000/llm/summary";

        FRAME_MS = 40;
        TARGET_SR = 16000;
        FRAME_SAMPLES = TARGET_SR * FRAME_MS / 1000; // 640
        FRAME_BYTES = FRAME_SAMPLES * 2; // 1280

        STR_POISON_PILL = "__STTEND__";
        STR_NEXT = "__NEXT__";
    }
}
