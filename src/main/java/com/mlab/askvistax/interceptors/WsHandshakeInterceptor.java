package com.mlab.askvistax.interceptors;

import com.mlab.askvistax.utils.CommonConstants;
import com.mlab.askvistax.utils.JwtUtil;
import com.mlab.askvistax.websocket.VideoStreamHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        Map<String, Object> claims;
        // token令牌验证[URL后加参数的方式]
        URI uri = request.getURI();
        String query = uri.getQuery();

        Map<String, String> params = Arrays.stream(query.split("&"))
                .map(s -> s.split("="))
                .collect(Collectors.toMap(a -> a[0], a -> URLDecoder.decode(a[1], StandardCharsets.UTF_8)));

        String token = params.get("Authorization");
        String interviewIdStr = params.get("interviewId");
        Integer interviewId = Integer.valueOf(interviewIdStr);
        log.info("Token: {}, InterviewId: {}", token, interviewId);

        if (token == null) {
            log.info("WsInterceptor: Unable to obtain the token included in the request");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);    // 设置HTTP响应状态码为未授权
            return false;
        }

        // 验证取出的token
        try {
            // 从redis中获取有效期内的相同token
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String redisToken = operations.get(token);
            if (redisToken == null) {   // token非法或已经失效
                throw new RuntimeException();
            }
            else {  // token合法，之后检测token中的身份是否对于此次请求合法
                claims = JwtUtil.parseToken(token); // 反序列化，解析token中的业务数据
                // 将反序列化后的claims存入当前连接session的attributes中
                attributes.put("claims", claims);
                attributes.put("interviewId", interviewId);  // 将面试ID存入session的attributes中
            }
        } catch (Exception e) {
            log.info("WsInterceptor: The Token Has Expired or is Invalid");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);    // 设置HTTP响应状态码为未授权
            return false;
        }

        // 针对不同的WebSocketHandler，判断此时的ws连接是否符合身份
        if (wsHandler instanceof VideoStreamHandler) {  // VideoStreamHandler
            String userAccount = (String) claims.get("userAccount");
            String userName = (String) claims.get("userName");
            Integer roleType = (Integer) claims.get("roleType");

            if (roleType != 2) {    // 非应聘者无法连接视频流
                log.info("账号名为: {}的用户: {}非应聘者，无法连接视频流面试！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
                response.setStatusCode(HttpStatus.FORBIDDEN);   // 设置HTTP响应状态码为403，表示权限错误
                return false;
            } else {
                return true;
            }
        }// TODO 其它的WebSocketHandler...
        return true;

    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("Handshake failed: {}", exception.getMessage(), exception);
        }
        else {
            log.info("WebSocket handshake finished. URI: {}", request.getURI());
        }
    }

}
