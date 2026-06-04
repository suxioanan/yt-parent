package com.yt.ocr.client;

import com.yt.ocr.config.OcrProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class OcrClient {

    private final RestTemplate restTemplate;
    private final OcrProperties properties;

    public OcrClient(RestTemplate restTemplate, OcrProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public String postFile(String endpoint, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getName();
                }
            };
            return doPost(endpoint, resource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }

    public String postMultipartFile(String endpoint, MultipartFile file) {
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    String name = file.getOriginalFilename();
                    return name != null ? name : "image.jpg";
                }
            };
            return doPost(endpoint, resource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read multipart file", e);
        }
    }

    public String postByUrl(String endpoint, String imageUrl) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + endpoint + "/url")
                .queryParam("url", imageUrl)
                .toUriString();
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
        return response.getBody();
    }

    public String postByPath(String endpoint, String imagePath) {
        return postFile(endpoint, new File(imagePath));
    }

    /**
     * POST multipart 文件，返回 JSON 字符串（用于 OCR 识别类接口）
     */
    private String doPost(String endpoint, ByteArrayResource resource) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String url = properties.getBaseUrl() + endpoint;
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        return response.getBody();
    }

    /**
     * POST multipart 文件（File），返回二进制字节（用于 docx->PDF 等文件下载类接口）
     */
    public byte[] postFileForBytes(String endpoint, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getName();
                }
            };
            return doPostForBytes(endpoint, resource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * POST multipart 文件（MultipartFile），返回二进制字节
     */
    public byte[] postMultipartFileForBytes(String endpoint, MultipartFile file) {
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    String name = file.getOriginalFilename();
                    return name != null ? name : "document.docx";
                }
            };
            return doPostForBytes(endpoint, resource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read multipart file", e);
        }
    }

    /**
     * POST multipart 并返回二进制流字节
     */
    private byte[] doPostForBytes(String endpoint, ByteArrayResource resource) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String url = properties.getBaseUrl() + endpoint;
        ResponseEntity<Resource> response = restTemplate.postForEntity(url, requestEntity, Resource.class);
        try (InputStream is = response.getBody().getInputStream()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read binary response from " + endpoint, e);
        }
    }

    /**
     * POST URL 参数（无需上传文件），返回二进制字节（用于 docx->PDF url 类接口）
     */
    public byte[] postUrlForBytes(String endpoint, String urlParam) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + endpoint + "/url")
                .queryParam("url", urlParam)
                .toUriString();
        ResponseEntity<Resource> response = restTemplate.postForEntity(url, null, Resource.class);
        try (InputStream is = response.getBody().getInputStream()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read binary response from URL: " + urlParam, e);
        }
    }
}
