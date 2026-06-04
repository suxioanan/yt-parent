package com.yt.ocr.config;

import com.yt.ocr.client.OcrClient;
import com.yt.ocr.service.impl.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@AutoConfiguration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate(OcrProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeout()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeout()));
        return new RestTemplate(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public OcrClient ocrClient(RestTemplate restTemplate, OcrProperties properties) {
        return new OcrClient(restTemplate, properties);
    }

    @Primary
    @Bean
    @ConditionalOnMissingBean
    public OcrChineseService ocrChineseService(OcrClient ocrClient, OcrProperties properties) {
        return new OcrChineseService(ocrClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OcrPlateService ocrPlateService(OcrClient ocrClient, OcrProperties properties) {
        return new OcrPlateService(ocrClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public StructureService structureService(OcrClient ocrClient, OcrProperties properties) {
        return new StructureService(ocrClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdCardService idCardService(OcrClient ocrClient, OcrProperties properties) {
        return new IdCardService(ocrClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WordToPdfService wordToPdfService(OcrClient ocrClient, OcrProperties properties) {
        return new WordToPdfService(ocrClient, properties);
    }
}
