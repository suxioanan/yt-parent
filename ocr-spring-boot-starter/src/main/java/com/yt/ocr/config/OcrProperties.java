package com.yt.ocr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    private String baseUrl = "http://172.16.41.107:8000";
    private int timeout = 120000;

    private Endpoint endpoints = new Endpoint();

    @Data
    public static class Endpoint {
        private String chinese = "/ocr/ch";
        private String plate = "/ocr/plate";
        private String structure = "/structure";
        private String wordtopdf = "/convert/docx-to-pdf";
    }
}
