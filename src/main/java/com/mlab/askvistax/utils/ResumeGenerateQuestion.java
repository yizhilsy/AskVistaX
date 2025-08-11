package com.mlab.askvistax.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ResumeGenerateQuestion {
    private final RestTemplate restTemplate = new RestTemplate();

    public List<String> generareQuestion(String resumeUrl) throws Exception {
        // 1. 下载文件到内存
        log.info("Downloading resume from: {}", resumeUrl);
        byte[] fileBytes = downloadFile(resumeUrl);

        // 2. 上传文件到目标接口
        return uploadFile(fileBytes, "resume.pdf"); // 文件名自己根据情况定
    }

    private byte[] downloadFile(String fileUrl) throws Exception {
        URL url = new URL(fileUrl);
        try (InputStream in = url.openStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[20480];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        }
    }

    private List<String> uploadFile(byte[] fileBytes, String filename) throws JsonProcessingException {
        // Multipart 表单
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename; // 必须重写，否则文件名可能为空
            }
        };
        body.add("file", fileResource); // 如果接口需要的字段不是 "file"，请改成对应字段名

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(CommonConstants.resumeQuestionUrl, requestEntity, String.class);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode questionsNode = root.path("data").path("questions");

        List<String> questionList = new ArrayList<>();

        if (questionsNode.isArray()) {
            for (JsonNode q : questionsNode) {
                questionList.add(q.path("question").asText());
            }
        }
        return questionList;

    }
}
