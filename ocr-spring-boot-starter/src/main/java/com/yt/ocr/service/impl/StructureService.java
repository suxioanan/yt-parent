package com.yt.ocr.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yt.ocr.client.OcrClient;
import com.yt.ocr.config.OcrProperties;
import com.yt.ocr.model.StructureItem;
import com.yt.ocr.model.StructureResult;
import com.yt.ocr.service.OcrOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 *  文档结构分析 — 表格(HTML)、标题、段落层级
 *  专门识别文档中的表格结构、标题层级、段落布局，返回 HTML 格式的表格和结构化文本
 * @author sunan
 * @date 2026/5/28
 * @param null
 * @return
 **/
public class StructureService implements OcrOperations<StructureResult> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OcrClient client;
    private final OcrProperties properties;

    public StructureService(OcrClient client, OcrProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public StructureResult ocr(File file) {
        String json = client.postFile(properties.getEndpoints().getStructure(), file);
        return parseStructureResult(json);
    }

    @Override
    public StructureResult ocr(MultipartFile file) throws IOException {
        String json = client.postMultipartFile(properties.getEndpoints().getStructure(), file);
        return parseStructureResult(json);
    }

    @Override
    public StructureResult ocrByUrl(String imageUrl) {
        String json = client.postByUrl(properties.getEndpoints().getStructure(), imageUrl);
        return parseStructureResult(json);
    }

    @Override
    public StructureResult ocrByPath(String imagePath) {
        String json = client.postByPath(properties.getEndpoints().getStructure(), imagePath);
        return parseStructureResult(json);
    }

    private StructureResult parseStructureResult(String json) {
        StructureResult result = new StructureResult();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            result.setSuccess(root.path("success").asBoolean(false));
            result.setCount(root.path("count").asInt(0));

            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray()) {
                List<StructureItem> items = new ArrayList<>();
                for (JsonNode itemNode : itemsNode) {
                    StructureItem item = new StructureItem();
                    item.setType(itemNode.path("type").asText(""));
                    item.setHtml(itemNode.path("html").asText(""));
                    item.setText(itemNode.path("text").asText(""));
                    items.add(item);
                }
                result.setItems(items);
            }
        } catch (JsonProcessingException e) {
            result.setSuccess(false);
        }
        return result;
    }
}
