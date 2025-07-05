package com.mlab.askvistax.interceptors;

import com.mlab.askvistax.anno.RequireRole;
import com.mlab.askvistax.utils.JwtUtil;
import com.mlab.askvistax.utils.ThreadLocalUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Map;

@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }
        Map<String, Object> claims;
        // token令牌验证
        String token = request.getHeader("Authorization");
        // 验证token
        try {
            // 从redis中获取有效期内的相同token
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String redisToken = operations.get(token);
            if (redisToken == null) {   // token非法或已经失效
                throw new RuntimeException();
            }
            else {  // token合法，之后检测token中的身份是否对于此次请求合法
                claims = JwtUtil.parseToken(token); // 反序列化，解析token中的业务数据
            }
        } catch (Exception e) {
            log.info("The Token Has Expired or is Invalid");
            response.setStatus(401);    // 设置HTTP响应状态码为401，表示未授权
            return false;
        }

        // Todo 验证@RequireRole中的身份要求是否满足
        if (handler instanceof HandlerMethod) {
            HandlerMethod method = (HandlerMethod) handler;
            RequireRole requireRole = method.getMethodAnnotation(RequireRole.class);

            if (requireRole != null) {
                // 获取当前用户的角色
                String currentUserRole = claims.get("usertype").toString();
                // 获取请求的接口授权的角色列表
                String[] requireRoles = requireRole.value();
                // 核验当前用户是否是授权的角色
                boolean hasRequiredRole = Arrays.asList(requireRoles).contains(currentUserRole);
                if (!hasRequiredRole) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied, You Don't Have The Required Role!");
                    return false;
                }
            }
        }

        // 把业务数据存储到ThreadLocal中
        ThreadLocalUtil.set(claims);
        return true;
    }


}
