package com.yt.ocr.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yt.ocr.model.OcrItem;
import com.yt.ocr.model.PlateResult;
import com.yt.ocr.client.OcrClient;
import com.yt.ocr.config.OcrProperties;
import com.yt.ocr.model.PlateColor;
import com.yt.ocr.service.OcrOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 车牌识别
 * @author sunan
 * @date 2026/5/28
 **/
public class OcrPlateService implements OcrOperations<PlateResult> {

    /**
     * 中国车牌号正则（去除间隔符后匹配）：
     * 组1: 省份简称  组2: 城市代号  组3: 车牌主体(5位普通 / 6位新能源)
     */
    private static final Pattern PLATE_PATTERN = Pattern.compile(
            "([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁])" +
            "([A-Z])" +
            "([A-Z0-9]{5,6})"
    );

    /** 省份简称集合 */
    private static final String PROVINCES = "京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OcrClient client;
    private final OcrProperties properties;

    public OcrPlateService(OcrClient client, OcrProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PlateResult ocr(File file) {
        String json = client.postFile(properties.getEndpoints().getPlate(), file);
        return parsePlateResult(json);
    }

    @Override
    public PlateResult ocr(MultipartFile file) throws IOException {
        String json = client.postMultipartFile(properties.getEndpoints().getPlate(), file);
        return parsePlateResult(json);
    }

    @Override
    public PlateResult ocrByUrl(String imageUrl) {
        String json = client.postByUrl(properties.getEndpoints().getPlate(), imageUrl);
        return parsePlateResult(json);
    }

    @Override
    public PlateResult ocrByPath(String imagePath) {
        String json = client.postByPath(properties.getEndpoints().getPlate(), imagePath);
        return parsePlateResult(json);
    }

    // ==================== 解析逻辑 ====================

    private PlateResult parsePlateResult(String json) {
        PlateResult result = new PlateResult();
        PlateColor plateColor = null;
        String rawText = "";
        List<OcrItem> items = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            result.setSuccess(root.path("success").asBoolean(false));
            rawText = root.path("text").asText("");
            result.setRawText(rawText);

            // 解析 items 列表
            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray()) {
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
            }

            // 解析车牌颜色
            JsonNode pcNode = root.path("plate_color");
            if (!pcNode.isMissingNode() && pcNode.isObject()) {
                plateColor = new PlateColor();
                plateColor.setType(pcNode.path("type").asText(""));
                plateColor.setName(pcNode.path("name").asText(""));
                plateColor.setDesc(pcNode.path("desc").asText(""));
                plateColor.setConfidence(pcNode.path("confidence").asDouble(0.0));
            }
        } catch (JsonProcessingException e) {
            result.setSuccess(false);
            result.setRawText("Parse error: " + e.getMessage());
            return result;
        }

        // 优先用 items 提取（更可靠），其次用 rawText 正则匹配
        PlateInfo info = extractPlateInfo(rawText, items);

        result.setPlateNumber(info.plateNumber);
        result.setProvince(info.province);
        result.setCityCode(info.cityCode);
        result.setNewEnergy(info.newEnergy);
        result.setPlateColor(plateColor);
        result.setPlateType(resolvePlateType(info.newEnergy, plateColor));
        result.setItems(items);

        // 置信度：优先取完整车牌号那个 item 的 confidence
        result.setConfidence(info.confidence > 0 ? info.confidence : avgConfidence(items));

        return result;
    }

    /**
     * 车牌解析信息
     */
    private static class PlateInfo {
        String plateNumber = "";
        String province = "";
        String cityCode = "";
        boolean newEnergy = false;
        double confidence = 0.0;
    }

    /**
     * 提取车牌信息：优先用 items 逐个拼接，回退到 rawText 正则匹配。
     */
    private PlateInfo extractPlateInfo(String rawText, List<OcrItem> items) {
        PlateInfo info = new PlateInfo();

        // ---- 策略 1：从 items 中找省份 + 拼接车牌 ----
        String province = null;
        String cityCode = null;
        StringBuilder body = new StringBuilder();
        double bestConfidence = 0.0;

        for (OcrItem item : items) {
            String t = item.getText();
            if (t == null || t.isEmpty()) continue;

            // 扫描省份字符
            if (province == null) {
                for (int i = 0; i < t.length(); i++) {
                    char c = t.charAt(i);
                    if (PROVINCES.indexOf(c) >= 0) {
                        province = String.valueOf(c);
                        break;
                    }
                }
            }

            // 收集字母数字作为车牌主体
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                    body.append(c);
                }
            }

            // 记录最高置信度
            if (item.getConfidence() > bestConfidence) {
                bestConfidence = item.getConfidence();
            }
        }

        if (province != null && body.length() >= 6) {
            // 第一个字母是城市代号
            String bodyStr = body.toString();
            // 找到第一个字母作为城市代号
            int firstLetterIdx = -1;
            for (int i = 0; i < bodyStr.length(); i++) {
                char c = bodyStr.charAt(i);
                if (c >= 'A' && c <= 'Z') {
                    firstLetterIdx = i;
                    cityCode = String.valueOf(c);
                    break;
                }
            }

            if (cityCode != null && firstLetterIdx >= 0) {
                // body = 城市代号后面的部分（5位普通 或 6位新能源）
                String bodyPart = bodyStr.substring(firstLetterIdx + 1);
                if (!bodyPart.isEmpty()) {
                    info.province = province;
                    info.cityCode = cityCode;
                    info.plateNumber = province + cityCode + bodyPart;
                    info.newEnergy = isNewEnergy(bodyPart, null);
                    info.confidence = bestConfidence;
                    return info;
                }
            }
        }

        // ---- 策略 2：回退到 rawText 正则匹配 ----
        if (rawText != null && !rawText.isEmpty()) {
            // 去掉间隔符（· \u00b7）和空格
            String text = rawText.replace("\u00b7", "").replace("·", "").replaceAll("\\s+", "");
            Matcher matcher = PLATE_PATTERN.matcher(text);

            if (matcher.find()) {
                info.province = matcher.group(1);
                info.cityCode = matcher.group(2);
                String bodyPart = matcher.group(3);
                info.plateNumber = info.province + info.cityCode + bodyPart;
                info.newEnergy = isNewEnergy(bodyPart, null);
                info.confidence = items.isEmpty() ? 0.0 : items.get(0).getConfidence();
                return info;
            }
        }

        // ---- 策略 3：兜底 — 直接返回去噪后的文本 ----
        if (rawText != null && !rawText.isEmpty()) {
            info.plateNumber = rawText.replaceAll("\\s+", "").trim();
        }
        info.confidence = bestConfidence;
        return info;
    }

    private boolean isNewEnergy(String body, PlateColor plateColor) {
        // 新能源车牌 body 长度为 6（普通车牌为 5）
        if (body != null && body.length() == 6) {
            return true;
        }
        if (plateColor != null && "green".equalsIgnoreCase(plateColor.getType())) {
            return true;
        }
        return false;
    }

    private String resolvePlateType(boolean isNewEnergy, PlateColor plateColor) {
        if (plateColor != null && plateColor.getName() != null && !plateColor.getName().isEmpty()) {
            return plateColor.getName();
        }
        return isNewEnergy ? "绿牌" : "蓝牌";
    }

    private double avgConfidence(List<OcrItem> items) {
        if (items == null || items.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (OcrItem item : items) {
            sum += item.getConfidence();
        }
        return sum / items.size();
    }
}
