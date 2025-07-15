package com.mlab.askvistax.utils;

import com.obs.services.ObsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Component
@Slf4j
public class HuaWeiOBSUtils {
    @Autowired
    private HuaWeiOBSProperties huaWeiOBSProperties;

    /**
     * 实现上传图片到OBS
     * @param file  上传的文件
     */
    public String upload(MultipartFile file) throws IOException {
        String endpoint = huaWeiOBSProperties.getEndpoint();
        String accessKeyId = huaWeiOBSProperties.getAccessKeyId();
        String accessKeySecret = huaWeiOBSProperties.getAccessKeySecret();
        String bucketName = huaWeiOBSProperties.getBucketName();

        // 获取上传的文件的输入流
        InputStream inputStream = file.getInputStream();

        // 避免文件覆盖，使用UUID生成唯一标识码
        String originalFilename = file.getOriginalFilename();
        String fileName = UUID.randomUUID().toString() + originalFilename.substring(originalFilename.lastIndexOf("."));

        //上传文件到 OBS
        ObsClient obsClient = new ObsClient(accessKeyId, accessKeySecret, endpoint);
        obsClient.putObject(bucketName, fileName, inputStream);

        //文件访问路径
        String url = endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + fileName;
        // 关闭obsClient
        obsClient.close();
        return url;
    }




}
