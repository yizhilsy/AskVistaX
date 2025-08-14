package com.mlab.askvistax.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.mlab.askvistax.utils.EncryptUtil;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.presets.opencv_core;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 实时转写调用demo
 * 此demo只是一个简单的调用示例，不适合用到实际生产环境中
 *
 * @author white
 *
 */
@Component
@Slf4j
public class RTASRTest {
    @Autowired
    private XfYunProperties xfYunProperties;
    // 请求地址
    private static final String HOST = "rtasr.xfyun.cn/v1/ws";

    private static final String BASE_URL = "wss://" + HOST;

    private static final String ORIGIN = "https://" + HOST;

    // 音频文件路径
    private static final String AUDIO_PATH = "src/main/resources/test_1.pcm";

    // 每次发送的数据大小 1280 字节
    private static final int CHUNCKED_SIZE = 1280;

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS");

    private URI url;
    private DraftWithOrigin draft;
    private CountDownLatch handshakeSuccess;
    private CountDownLatch connectClose;
    private MyWebSocketClient client;


    public void start(BlockingQueue<String> sttResultQueue) throws Exception {
        url = new URI(BASE_URL + getHandShakeParams(xfYunProperties.getAPPID(), xfYunProperties.getSECRETKEY()));
        draft = new DraftWithOrigin(ORIGIN);
        handshakeSuccess = new CountDownLatch(1);
        connectClose = new CountDownLatch(1);
        client = new MyWebSocketClient(url, draft, handshakeSuccess, connectClose, sttResultQueue);
        client.connect();
        while (!client.getReadyState().equals(WebSocket.READYSTATE.OPEN)) {
            log.info("xfyun websocket连接中, time: {}", getCurrentTimeStr());
            Thread.sleep(500);
        }
        // 等待握手成功
        handshakeSuccess.await();
        log.info("xfyun websocket连接成功, time: {}", sdf.format(new Date()));
    }


    public void sendPCMData(byte[] pcmData) {
        if (client != null && client.isOpen()) {
            try {
                send(client, pcmData);
            } catch (Exception e) {
                log.error("发送PCM数据失败", e);
            }
        }
        else {
            log.warn("WebSocket 未连接，无法发送 PCM 数据");
        }
    }

    public void shutdown() {
        try{
            // 发送结束标识
            send(client,"{\"end\": true}".getBytes());
            log.info("xfyun websocket发送结束标识完成, time: {}", getCurrentTimeStr());
            // 等待连接关闭
            connectClose.await();
        } catch (Exception e) {
            log.info("关闭websocket client失败", e);
        }

    }


//    public static void main(String[] args) throws Exception {
//        while (true) {
//            URI url = new URI(BASE_URL + getHandShakeParams(APPID, SECRET_KEY));
//            DraftWithOrigin draft = new DraftWithOrigin(ORIGIN);
//            CountDownLatch handshakeSuccess = new CountDownLatch(1);
//            CountDownLatch connectClose = new CountDownLatch(1);
//            MyWebSocketClient client = new MyWebSocketClient(url, draft, handshakeSuccess, connectClose);
//
//            client.connect();
//
//            while (!client.getReadyState().equals(WebSocket.READYSTATE.OPEN)) {
//                System.out.println(getCurrentTimeStr() + "\t连接中");
//                Thread.sleep(1000);
//            }
//
//            // 等待握手成功
//            handshakeSuccess.await();
//            System.out.println(sdf.format(new Date()) + " 开始发送音频数据");
//            // 发送音频
//            byte[] bytes = new byte[CHUNCKED_SIZE];
//            try (RandomAccessFile raf = new RandomAccessFile(AUDIO_PATH, "r")) {
//                int len = -1;
//                long lastTs = 0;
//                while ((len = raf.read(bytes)) != -1) {
//                    if (len < CHUNCKED_SIZE) {
//                        send(client, bytes = Arrays.copyOfRange(bytes, 0, len));
//                        break;
//                    }
//
//                    long curTs = System.currentTimeMillis();
//                    if (lastTs == 0) {
//                        lastTs = System.currentTimeMillis();
//                    } else {
//                        long s = curTs - lastTs;
//                        if (s < 40) {
//                            System.out.println("error time interval: " + s + " ms");
//                        }
//                    }
//                    send(client, bytes);
//                    // 每隔40毫秒发送一次数据
//                    Thread.sleep(40);
//                }
//
//                // 发送结束标识
//                send(client,"{\"end\": true}".getBytes());
//                System.out.println(getCurrentTimeStr() + "\t发送结束标识完成");
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//            // 等待连接关闭
//            connectClose.await();
//            break;
//        }
//    }

    // 生成握手参数
    public static String getHandShakeParams(String appId, String secretKey) {
        String ts = System.currentTimeMillis()/1000 + "";
        String signa = "";
        try {
            signa = EncryptUtil.HmacSHA1Encrypt(EncryptUtil.MD5(appId + ts), secretKey);
            return "?appid=" + appId + "&ts=" + ts + "&signa=" + URLEncoder.encode(signa, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public static void send(WebSocketClient client, byte[] bytes) {
        if (client.isClosed()) {
            throw new RuntimeException("client connect closed!");
        }

        client.send(bytes);
    }

    public static String getCurrentTimeStr() {
        return sdf.format(new Date());
    }

    public static class MyWebSocketClient extends WebSocketClient {

        private CountDownLatch handshakeSuccess;
        private CountDownLatch connectClose;
        private BlockingQueue<String> sttResultQueue;


        public MyWebSocketClient(URI serverUri, Draft protocolDraft, CountDownLatch handshakeSuccess, CountDownLatch connectClose,
                                 BlockingQueue<String> sttResultQueue) {
            super(serverUri, protocolDraft);
            this.handshakeSuccess = handshakeSuccess;
            this.connectClose = connectClose;
            this.sttResultQueue = sttResultQueue;

            if(serverUri.toString().contains("wss")){
                trustAllHosts(this);
            }
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            System.out.println(getCurrentTimeStr() + "\t连接建立成功！");
        }

        @Override
        public void onMessage(String msg) {
            JSONObject msgObj = JSON.parseObject(msg);
            String action = msgObj.getString("action");
            if (Objects.equals("started", action)) {
                // 握手成功
                System.out.println(getCurrentTimeStr() + "\t握手成功！sid: " + msgObj.getString("sid"));
                handshakeSuccess.countDown();
            } else if (Objects.equals("result", action)) {
                // 转写结果
                STTTranscription sttTranscription = getContent(msgObj.getString("data"));

//                try {
//                    if (conCurrentSession != null && conCurrentSession.isOpen()) {
//                        log.info("ws服务端正常！");
//                    }
//                    log.info("xfyun send sessionId: {}", conCurrentSession.getId());
//                    JSONObject sttTranscriptionJson = new JSONObject();
//                    sttTranscriptionJson.put("type", "transcript");
//                    sttTranscriptionJson.put("content", sttTranscription.getText());
//                    if (sttTranscription.isFinal()) {
//                        conCurrentSession.sendMessage(new TextMessage(sttTranscriptionJson.toJSONString()));
//                    }
//                    log.info("session发送数据: {}", sttTranscriptionJson.toJSONString());
//                } catch (Exception e) {
//                    log.error("websocket发送消息失败", e);
//                }


//                log.info("xfyun rtasr text: {}, isFinal: {}", sttTranscription.getText(), sttTranscription.isFinal());

                // 判断此时是否是句子的末尾，非末尾不放入队列
                if (sttTranscription.isFinal()) {
                    // 将结果放入阻塞队列中，供其他线程读取
                    try {
//                        log.info("xfyun rtasr 将转录文本放入sttResultQueue队列");
                        sttResultQueue.offer(sttTranscription.getText());
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                        log.error("放入sttResultQueue队列时被中断", e);
                    }
                }
            } else if (Objects.equals("error", action)) {
                // 连接发生错误
                System.out.println("Error: " + msg);
                System.exit(0);
            }
        }

        @Override
        public void onError(Exception e) {
            System.out.println(getCurrentTimeStr() + "\t连接发生错误：" + e.getMessage() + ", " + new Date());
            e.printStackTrace();
            System.exit(0);
        }

        @Override
        public void onClose(int arg0, String arg1, boolean arg2) {
            System.out.println(getCurrentTimeStr() + "\tXfYun链接关闭");
            try {
                sttResultQueue.offer(CommonConstants.STR_POISON_PILL);
            } catch (Exception e) {
                log.error("发送结束信号失败", e);
            }
            connectClose.countDown();
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                System.out.println(getCurrentTimeStr() + "\t服务端返回：" + new String(bytes.array(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        public void trustAllHosts(MyWebSocketClient appClient) {
            System.out.println("wss");
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }

                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }
            }};

            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                appClient.setSocket(sc.getSocketFactory().createSocket());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 把转写结果解析为句子
    public static STTTranscription getContent(String message) {
        StringBuffer resultBuilder = new StringBuffer();
        boolean isFinal = false;
        Long bg = 0L;
        Long ed = 0L;
        try {
            JSONObject messageObj = JSON.parseObject(message);
            JSONObject cn = messageObj.getJSONObject("cn");
//            log.info(JSON.toJSONString(cn, SerializerFeature.PrettyFormat));
            JSONObject st = cn.getJSONObject("st");

            String typeStr = st.getString("type");
            isFinal = "0".equals(String.valueOf(typeStr));
            bg = parseLongObj(st.get("bg"));
            ed = parseLongObj(st.get("ed"));

            JSONArray rtArr = st.getJSONArray("rt");
            for (int i = 0; i < rtArr.size(); i++) {
                JSONObject rtArrObj = rtArr.getJSONObject(i);
                JSONArray wsArr = rtArrObj.getJSONArray("ws");
                for (int j = 0; j < wsArr.size(); j++) {
                    JSONObject wsArrObj = wsArr.getJSONObject(j);
                    JSONArray cwArr = wsArrObj.getJSONArray("cw");

                    // 在 cw 候选中选取置信度最高的
                    double bestSc = Double.NEGATIVE_INFINITY;
                    String bestWStr = null;
                    for (int k = 0; k < cwArr.size(); k++) {
                        JSONObject cwArrObj = cwArr.getJSONObject(k);
                        Object scObj = cwArrObj.get("sc");
                        double sc = Double.parseDouble(String.valueOf(scObj));
                        String wStr = cwArrObj.getString("w");
                        if (sc > bestSc) {
                            bestSc = sc;
                            bestWStr = wStr;
                        }
                    }
                    resultBuilder.append(bestWStr);
                }
            }
        } catch (Exception e) {
            log.info("getContent解析转写结果失败: {}", e.getMessage());
        }

        return new STTTranscription(
                resultBuilder.toString(),
                isFinal,
                bg,
                ed
        );
    }

    // 把可能为数字或字符串的对象解析成 Long
    public static Long parseLongObj(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Number) return ((Number) o).longValue();
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception ex) {
            return null;
        }
    }
}
