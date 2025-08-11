package com.mlab.askvistax.utils;

import org.bytedeco.javacv.Frame;

import java.util.HashMap;
import java.util.Map;

public class CommonConstants {
    public static Map<Integer, String> roleTypeMap = new HashMap<>();
    public static String resumeQuestionUrl;
    public static String createAgentUrl;
    public static String ttsUrl;
    public static String sttUrl;
    // 定义一个单例结束标记 Frame 对象
    public static final Frame POISON_PILL = new Frame();

    static {
        roleTypeMap.put(0, "Admin");
        roleTypeMap.put(1, "Interviewer");
        roleTypeMap.put(2, "Candidate");
        resumeQuestionUrl = "http://58.199.161.182:8000/utils/questions/upload-resume-file";
        createAgentUrl = "http://58.199.161.182:8000/llm/message/create";
        ttsUrl = "http://58.199.161.182:8000/audio/tts/synthesize";
        sttUrl = "http://58.199.161.182:8000/audio/upload";
    }
}
