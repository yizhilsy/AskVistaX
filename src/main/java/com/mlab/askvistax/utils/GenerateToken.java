package com.mlab.askvistax.utils;

import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GenerateToken {
    public String generate(String uid, String userAccount, String userName, Integer roleType) {
        // 构造业务数据
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", uid);
        claims.put("userAccount", userAccount);
        claims.put("userName", userName);
        claims.put("roleType", roleType);
        // 颁发新令牌
        String token = JwtUtil.genToken(claims);
        return token;
    }
}
