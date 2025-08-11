package com.mlab.askvistax.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.Map;

@Component
public class HttpClientUtil {

    private final RestTemplate restTemplate;

    public HttpClientUtil() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 发送GET请求，返回响应体字符串
     * @param url 请求地址
     * @param headers 额外请求头（可传null）
     * @return 响应体
     */
    public String get(String url, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        HttpEntity<Void> requestEntity = new HttpEntity<>(httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
        return response.getBody();
    }

    /**
     * 发送POST请求，默认Content-Type: application/json，传递JSON字符串
     * @param url 请求地址
     * @param jsonBody JSON字符串请求体
     * @param headers 额外请求头（可传null）
     * @return 响应体
     */
    public String postJson(String url, String jsonBody, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, httpHeaders);
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        return response.getBody();
    }

    /**
     * 发送POST请求，表单提交
     * @param url 请求地址
     * @param formData 键值对表单数据
     * @param headers 额外请求头（可传null）
     * @return 响应体
     */
    public String postForm(String url, Map<String, String> formData, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        if (formData != null) {
            formData.forEach(map::add);
        }
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(map, httpHeaders);
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        return response.getBody();
    }

    /**
     * 发送POST请求，传递JSON字符串，返回二进制文件内容（byte数组）
     * @param url 请求地址
     * @param jsonBody JSON字符串请求体
     * @param headers 额外请求头（可传null）
     * @return 返回接口返回的文件字节数组
     */
    public byte[] postJsonForFile(String url, String jsonBody, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, httpHeaders);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                byte[].class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else {
            throw new RuntimeException("请求文件失败，HTTP状态码：" + response.getStatusCode());
        }
    }

    /**
     * 发送POST请求，multipart/form-data 格式上传音频文件，并返回 JSON 响应
     *
     * @param url 请求地址
     * @param audioFile 要上传的音频文件
     * @param headers 额外请求头（可传null）
     * @param formFields 除文件外的额外表单字段（可传null）
     * @return 接口返回的 JSON 字符串
     */
    public JsonNode postAudioFile(String url, File audioFile, Map<String, String> headers, Map<String, String> formFields) {
        if (audioFile == null || !audioFile.exists()) {
            throw new IllegalArgumentException("音频文件不存在: " + (audioFile != null ? audioFile.getAbsolutePath() : "null"));
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // 添加文件
        body.add("file", new FileSystemResource(audioFile));

        // 添加额外字段
        if (formFields != null) {
            formFields.forEach(body::add);
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, httpHeaders);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);
        return response.getBody();
    }
}

