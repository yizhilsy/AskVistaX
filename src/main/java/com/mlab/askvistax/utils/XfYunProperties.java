package com.mlab.askvistax.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "xfyun")
@Data
public class XfYunProperties {
    private String APPID;
    private String SECRETKEY;
}
