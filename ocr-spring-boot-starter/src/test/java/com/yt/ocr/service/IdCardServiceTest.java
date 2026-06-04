package com.yt.ocr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yt.ocr.model.OcrResult;
import com.yt.ocr.model.PlateResult;
import com.yt.ocr.client.OcrClient;
import com.yt.ocr.config.OcrProperties;
import com.yt.ocr.model.IdCardResult;
import com.yt.ocr.model.StructureResult;
import com.yt.ocr.service.impl.*;
import com.yt.third.ocr.service.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdCardServiceTest {

    @Mock
    private OcrClient ocrClient;

    @Mock
    private OcrProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void test() throws Exception {
        // 配置真实的 PaddleOCR 服务地址
        OcrProperties props = new OcrProperties();
        props.setBaseUrl("http://172.16.41.107:8000");
        props.setTimeout(2000000000);

        // 创建 RestTemplate
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getTimeout()));
        factory.setReadTimeout(Duration.ofMillis(props.getTimeout()));
        RestTemplate restTemplate = new RestTemplate(factory);

        OcrClient ocrClient = new OcrClient(restTemplate, props);
        ObjectMapper mapper = new ObjectMapper();

        System.out.println(">>> Request URL: " + props.getBaseUrl() + props.getEndpoints().getStructure());
        IdCardService service = new IdCardService(ocrClient, props);

        OcrPlateService PlateService = new OcrPlateService(ocrClient, props);

        StructureService structureService = new StructureService(ocrClient, props);
        OcrChineseService ocrChineseService=new OcrChineseService(ocrClient, props);
        WordToPdfService wordToPdfService = new WordToPdfService(ocrClient, props);

        String path = "/Users/sunan/Downloads/微信图片_正面.jpg";

        String path1 = "/Users/sunan/java_project/demo/ocr/ocr-spring-boot-starter/docker/7d426df8d3145e2b.jpg";;


        String path2 = "/Users/sunan/Downloads/微信图片_20260528093539_34_198.png";
        String path3 = "/Users/sunan/java_project/demo/ocr/ocr-spring-boot-starter/ocr_test_doc.png";




// 改成：



        StructureResult structureResult = structureService.ocrByPath(path3);

        byte[] pdfBytes = wordToPdfService.ocrByPath("/Users/sunan/Downloads/副本标准房屋租赁合同.docx");

// 保存到文件
        java.nio.file.Files.write(
                java.nio.file.Paths.get("/Users/sunan/Downloads/output.pdf"),
                pdfBytes
        );




        OcrResult ocrResult = ocrChineseService.ocrByPath(path2);

        PlateResult plateResult = PlateService.ocrByPath(path1);
        IdCardResult result = service.parseIdCardByUrl("http://127.0.0.1:9000/wuliu/idcard/b403135f-843e-4b9a-8360-0da1df6fb0b2-tmp_aec81dd8f87dee9fc6158f2049833fa5.jpg");

        assertNotNull(result);
        assertNotNull(result.getName(), "姓名不应为空");
        assertNotNull(result.getIdNumber(), "身份证号不应为空");
        System.out.println(result);
    }
    @Test
    void parseIdCardByPath_shouldParseAllFields() {
        OcrProperties.Endpoint endpoints = new OcrProperties.Endpoint();
        endpoints.setChinese("/ocr/ch");
        when(properties.getEndpoints()).thenReturn(endpoints);

        String mockJson = """
                {
                    "success": true,
                    "count": 8,
                    "text": "姓名尹涛性别女民族汉族出生1991年3月5日住址湖北省武汉市洪山区斗角村一组9号公民身份号码420111199103055829",
                    "items": [
                        {"text": "姓名尹", "confidence": 0.95, "box": [10, 20, 80, 20, 80, 50, 10, 50]},
                        {"text": "涛", "confidence": 0.93, "box": [85, 20, 110, 20, 110, 50, 85, 50]},
                        {"text": "性别女民族汉", "confidence": 0.96, "box": [10, 60, 160, 60, 160, 90, 10, 90]},
                        {"text": "出生1991年3月5日", "confidence": 0.97, "box": [10, 100, 200, 100, 200, 130, 10, 130]},
                        {"text": "住址湖北省武汉市洪山区", "confidence": 0.94, "box": [10, 140, 280, 140, 280, 170, 10, 170]},
                        {"text": "斗角村一组9号", "confidence": 0.92, "box": [10, 175, 200, 175, 200, 205, 10, 205]},
                        {"text": "公民身份号码420111199103055829", "confidence": 0.98, "box": [10, 240, 350, 240, 350, 270, 10, 270]},
                        {"text": "中华人民共和国", "confidence": 0.99, "box": [10, 0, 180, 0, 180, 30, 10, 30]}
                    ]
                }""";

        when(ocrClient.postByPath("/ocr/ch", "/test/idcard.jpg")).thenReturn(mockJson);

        IdCardService service = new IdCardService(ocrClient, properties);
        IdCardResult result = service.parseIdCardByPath("/test/idcard.jpg");

        assertNotNull(result);
        assertEquals("尹涛", result.getName());
        assertEquals("420111199103055829", result.getIdNumber());
        assertEquals("女", result.getGender());
        assertEquals("汉", result.getEthnicity());
        assertEquals("1991-03-05", result.getBirthDate());
        assertTrue(result.getAddress().contains("湖北省武汉市洪山区"));
        assertTrue(result.getAddress().contains("斗角村一组9号"));
    }

    @Test
    void parseIdCard_shouldHandleNullInput() {
        IdCardService service = new IdCardService(null, null);
        IdCardResult result = service.parseIdCard((OcrResult) null);

        assertNotNull(result);
        assertNull(result.getName());
        assertNull(result.getIdNumber());
    }

    @Test
    void parseIdCard_shouldHandleEmptyItems() {
        OcrResult emptyResult = new OcrResult();
        emptyResult.setSuccess(false);

        IdCardService service = new IdCardService(null, null);
        IdCardResult result = service.parseIdCard(emptyResult);

        assertNotNull(result);
        assertNull(result.getName());
        assertNull(result.getIdNumber());
    }
}
