package com.yt.ocr.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yt.ocr.model.OcrItem;
import com.yt.ocr.model.OcrResult;
import com.yt.ocr.client.OcrClient;
import com.yt.ocr.config.OcrProperties;
import com.yt.ocr.service.OcrOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文字识别
 * @author sunan
 * @date 2026/5/28
 **/
public class OcrChineseService implements OcrOperations<OcrResult> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OcrClient client;
    private final OcrProperties properties;

    public OcrChineseService(OcrClient client, OcrProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public OcrResult ocr(File file) {
        String json = client.postFile(properties.getEndpoints().getChinese(), file);
        return parseOcrResult(json);
    }

    @Override
    public OcrResult ocr(MultipartFile file) throws IOException {
        String json = client.postMultipartFile(properties.getEndpoints().getChinese(), file);
        return parseOcrResult(json);
    }

    @Override
    public OcrResult ocrByUrl(String imageUrl) {
        String json = client.postByUrl(properties.getEndpoints().getChinese(), imageUrl);
        return parseOcrResult(json);
    }

    @Override
    public OcrResult ocrByPath(String imagePath) {
        String json = client.postByPath(properties.getEndpoints().getChinese(), imagePath);
        return parseOcrResult(json);
    }

    private OcrResult parseOcrResult(String json) {
        OcrResult result = new OcrResult();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            result.setSuccess(root.path("success").asBoolean(false));
            result.setText(root.path("text").asText(""));
            result.setCount(root.path("count").asInt(0));

            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray()) {
                List<OcrItem> items = new ArrayList<>();
                for (JsonNode itemNode : itemsNode) {
                    OcrItem item = new OcrItem();
                    item.setText(itemNode.path("text").asText(""));
                    item.setConfidence(itemNode.path("confidence").asDouble(0.0));

                    JsonNode boxNode = itemNode.path("box");
                    if (boxNode.isArray()) {
                        double[] box = new double[boxNode.size()];
                        for (int i = 0; i < boxNode.size(); i++) {
                            box[i] = boxNode.get(i).asDouble();
                        }
                        item.setBox(box);
                    }
                    items.add(item);
                }
                result.setItems(items);
            }
        } catch (JsonProcessingException e) {
            result.setSuccess(false);
            result.setText("Parse error: " + e.getMessage());
        }
        return result;
    }
}
