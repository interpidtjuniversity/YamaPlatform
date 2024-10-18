package com.clcy.grade_feedback.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;

@Service
public class ALiYunOssServiceImpl implements ALiYunOssService, InitializingBean {

    private OSS ossClient;

    private static final String endpoint = "https://oss-cn-shanghai.aliyuncs.com";

    private static final String bucketName = "aliyun-wb-ei1y786hj2";

    private static final String dir = "feed_back/";

    private static final int expirationOffset = 3;

    @Override
    public void afterPropertiesSet() throws Exception {
        EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
        ossClient = new OSSClientBuilder().build(endpoint, credentialsProvider);
    }


    @Override
    public String upload(String id, InputStream data) {
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setExpirationTime(nextOffsetDate(expirationOffset));
            ossClient.putObject(bucketName, dir + id, data, meta);
            return gerUrl(id);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String gerUrl(String id) {
        Date expiration = nextOffsetDate(expirationOffset);
        URL url = ossClient.generatePresignedUrl(bucketName, dir + id, expiration);
        if (null != url) {
            return url.toString();
        }
        return null;
    }

    private static Date nextOffsetDate(int offset) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, offset);
        return calendar.getTime();
    }
}
