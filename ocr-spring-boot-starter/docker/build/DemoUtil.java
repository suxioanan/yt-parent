package com.honeycombis.honeycom.third.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author sunan
 */
public class DemoUtil {

    // ============================================================
    // PaddleOCR 配置
    // ============================================================
    private static final String OCR_BASE_URL = "http://172.16.41.107:8000";

    // ============================================================
    // 方式一：传 File 对象（本地路径）
    // ============================================================
    public static OcrResult ocrChinese(File imageFile) {
        return postOcr("/ocr/ch", imageFile);
    }

    public static OcrResult ocrPlate(File imageFile) {
        return postOcr("/ocr/plate", imageFile);
    }

    public static StructureResult structureAnalysis(File imageFile) {
        return postStructure(imageFile);
    }

    // ============================================================
    // 方式二：传网络 URL（服务端下载）
    // ============================================================
    public static OcrResult ocrChineseByUrl(String imageUrl) {
        return postOcrByUrl("/ocr/ch", imageUrl);
    }

    public static OcrResult ocrPlateByUrl(String imageUrl) {
        return postOcrByUrl("/ocr/plate", imageUrl);
    }

    public static StructureResult structureAnalysisByUrl(String imageUrl) {
        return postStructureByUrl(imageUrl);
    }

    // ============================================================
    // 方式三：传 MultipartFile（Spring 文件上传）
    // ============================================================
    public static OcrResult ocrChinese(MultipartFile multipartFile) throws IOException {
        return postOcr("/ocr/ch", multipartFile);
    }

    public static OcrResult ocrPlate(MultipartFile multipartFile) throws IOException {
        return postOcr("/ocr/plate", multipartFile);
    }

    public static StructureResult structureAnalysis(MultipartFile multipartFile) throws IOException {
        return postStructure(multipartFile);
    }

    // ============================================================
    // 健康检查
    // ============================================================
    public static boolean healthCheck() {
        try {
            String body = HttpUtil.get(OCR_BASE_URL + "/");
            return body != null && body.contains("\"running\"");
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================
    // 内部实现
    // ============================================================

    private static OcrResult postOcr(String endpoint, File imageFile) {
        String body = HttpRequest.post(OCR_BASE_URL + endpoint)
                .form("file", imageFile)
                .timeout(120000)
                .execute()
                .body();
        return parseOcrResult(body);
    }

    private static OcrResult postOcr(String endpoint, MultipartFile multipartFile) throws IOException {
        String fileName = multipartFile.getOriginalFilename();
        if (fileName == null) fileName = "image.jpg";
        String body = HttpRequest.post(OCR_BASE_URL + endpoint)
                .form("file", multipartFile.getBytes(), fileName)
                .timeout(120000)
                .execute()
                .body();
        return parseOcrResult(body);
    }

    private static OcrResult postOcrByUrl(String endpoint, String imageUrl) {
        String url = OCR_BASE_URL + endpoint + "/url?url=" + HttpUtil.encodeParams(imageUrl, StandardCharsets.UTF_8);
        String body = HttpRequest.post(url)
                .timeout(120000)
                .execute()
                .body();
        return parseOcrResult(body);
    }

    private static StructureResult postStructure(File imageFile) {
        String body = HttpRequest.post(OCR_BASE_URL + "/structure")
                .form("file", imageFile)
                .timeout(120000)
                .execute()
                .body();
        return parseStructureResult(body);
    }

    private static StructureResult postStructure(MultipartFile multipartFile) throws IOException {
        String fileName = multipartFile.getOriginalFilename();
        if (fileName == null) fileName = "doc.jpg";
        String body = HttpRequest.post(OCR_BASE_URL + "/structure")
                .form("file", multipartFile.getBytes(), fileName)
                .timeout(120000)
                .execute()
                .body();
        return parseStructureResult(body);
    }

    private static StructureResult postStructureByUrl(String imageUrl) {
        String url = OCR_BASE_URL + "/structure/url?url=" + HttpUtil.encodeParams(imageUrl, StandardCharsets.UTF_8);
        String body = HttpRequest.post(url)
                .timeout(120000)
                .execute()
                .body();
        return parseStructureResult(body);
    }

    // ============================================================
    // JSON 解析
    // ============================================================

    private static OcrResult parseOcrResult(String jsonStr) {
        OcrResult result = new OcrResult();
        try {
            JSONObject json = new JSONObject(jsonStr);
            result.success = json.getBool("success", false);
            result.text = json.getStr("text", "");
            result.count = json.getInt("count", 0);

            JSONArray items = json.getJSONArray("items");
            if (items != null) {
                result.items = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    OcrItem oi = new OcrItem();
                    oi.text = item.getStr("text", "");
                    oi.confidence = item.getDouble("confidence", 0.0);
                    JSONArray box = item.getJSONArray("box");
                    if (box != null) {
                        oi.box = new double[box.size()];
                        for (int j = 0; j < box.size(); j++) {
                            oi.box[j] = box.getDouble(j);
                        }
                    }
                    result.items.add(oi);
                }
            }

            // 解析车牌颜色
            JSONObject pc = json.getJSONObject("plate_color");
            if (pc != null) {
                PlateColor color = new PlateColor();
                color.type = pc.getStr("type", "");
                color.name = pc.getStr("name", "");
                color.desc = pc.getStr("desc", "");
                color.confidence = pc.getDouble("confidence", 0.0);
                result.plateColor = color;
            }
        } catch (Exception e) {
            result.success = false;
            result.text = "Parse error: " + e.getMessage();
        }
        return result;
    }

    private static StructureResult parseStructureResult(String jsonStr) {
        StructureResult result = new StructureResult();
        try {
            JSONObject json = new JSONObject(jsonStr);
            result.success = json.getBool("success", false);
            result.count = json.getInt("count", 0);

            JSONArray items = json.getJSONArray("items");
            if (items != null) {
                result.items = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    StructureItem si = new StructureItem();
                    si.type = item.getStr("type", "");
                    si.html = item.getStr("html", "");
                    si.text = item.getStr("text", "");
                    result.items.add(si);
                }
            }
        } catch (Exception e) {
            result.success = false;
        }
        return result;
    }

    // ============================================================
    // POJO
    // ============================================================

    public static class OcrItem {
        private String text;
        private double confidence;
        private double[] box;

        public String getText() { return text; }
        public double getConfidence() { return confidence; }
        public double[] getBox() { return box; }
    }

    public static class PlateColor {
        private String type;
        private String name;
        private String desc;
        private double confidence;

        public String getType() { return type; }
        public String getName() { return name; }
        public String getDesc() { return desc; }
        public double getConfidence() { return confidence; }

        @Override
        public String toString() {
            return name + "(" + desc + ")";
        }
    }

    public static class OcrResult {
        private boolean success;
        private String text;
        private int count;
        private List<OcrItem> items;
        private PlateColor plateColor;

        public boolean isSuccess() { return success; }
        public String getText() { return text; }
        public int getCount() { return count; }
        public List<OcrItem> getItems() { return items; }
        public PlateColor getPlateColor() { return plateColor; }

        @Override
        public String toString() {
            return "OcrResult{success=" + success + ", text='" + text + "', count=" + count + "}";
        }
    }

    public static class StructureItem {
        private String type;
        private String html;
        private String text;

        public String getType() { return type; }
        public String getHtml() { return html; }
        public String getText() { return text; }
    }

    public static class StructureResult {
        private boolean success;
        private int count;
        private List<StructureItem> items;

        public boolean isSuccess() { return success; }
        public int getCount() { return count; }
        public List<StructureItem> getItems() { return items; }

        @Override
        public String toString() {
            return "StructureResult{success=" + success + ", count=" + count + "}";
        }
    }

    // ============================================================
    // 身份证信息解析
    // ============================================================

    public static class IdCardResult {
        private String name;
        private String idNumber;
        private String address;
        private String gender;
        private String ethnicity;
        private String birthDate;

        public String getName() { return name; }
        public String getIdNumber() { return idNumber; }
        public String getAddress() { return address; }
        public String getGender() { return gender; }
        public String getEthnicity() { return ethnicity; }
        public String getBirthDate() { return birthDate; }

        @Override
        public String toString() {
            return "IdCardResult{" +
                    "name='" + name + '\'' +
                    ", idNumber='" + idNumber + '\'' +
                    ", address='" + address + '\'' +
                    ", gender='" + gender + '\'' +
                    ", ethnicity='" + ethnicity + '\'' +
                    ", birthDate='" + birthDate + '\'' +
                    '}';
        }
    }

    /**
     * 从 OCR 结果中提取身份证结构化信息
     * <p>
     * 身份证 OCR 按空间顺序返回文本行，逻辑顺序被打乱。
     * 例如姓名"尹涛"可能分成两行"姓名尹"和"涛"，
     * 而地址后半段"斗角村一组9号"可能乱入在姓名行之间。
     * 本方法用关键词 + 地址特征字词来正确归类。
     */
    public static IdCardResult parseIdCard(OcrResult ocrResult) {
        IdCardResult card = new IdCardResult();
        if (ocrResult == null || ocrResult.getItems() == null) return card;

        List<String> lines = new ArrayList<>();
        for (OcrItem item : ocrResult.getItems()) {
            lines.add(item.getText().trim());
        }

        String allText = String.join("", lines);

        // ─── 身份证号 ───
        Pattern idPattern = Pattern.compile("[1-9]\\d{16}[0-9Xx]");
        Matcher m = idPattern.matcher(allText);
        if (m.find()) {
            card.idNumber = m.group().toUpperCase();
        }

        // ─── 出生日期 ───
        if (card.idNumber != null && card.idNumber.length() >= 14) {
            card.birthDate = card.idNumber.substring(6, 10) + "-"
                    + card.idNumber.substring(10, 12) + "-"
                    + card.idNumber.substring(12, 14);
        }

        // ─── 性别 ───
        for (String line : lines) {
            if (line.contains("男")) { card.gender = "男"; break; }
            if (line.contains("女")) { card.gender = "女"; break; }
        }

        // ─── 民族 ───
        for (String line : lines) {
            if (line.contains("民族")) {
                card.ethnicity = line.replaceAll(".*民族\\s*", "").trim();
                break;
            }
        }

        // 地址特征字：文本中包含以下字样的，判定为地址片段
        String addrKeywords = "村|组|号|镇|乡|县|市|区|路|街|巷|弄|栋|单元|室|楼|层";
        Pattern addrPattern = Pattern.compile(addrKeywords);

        // ─── 姓名 ───
        // OCR 空间顺序可能使姓名分拆在间隔行中。
        // 例："姓名尹" → "斗角村一组9号"(地址) → "涛"
        // 策略：取"姓名"行内容 + 后续行中的短纯中文文本（1-2字，无地址特征）
        StringBuilder nameBuf = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("姓名")) {
                // 取"姓名"后面的文字
                nameBuf.append(line.replaceAll(".*姓名\\s*", "").trim());
                // 向后继续扫描 5 行，收集姓名续行
                for (int j = i + 1; j < Math.min(i + 5, lines.size()); j++) {
                    String next = lines.get(j).trim();
                    // 姓名续行的特征：纯中文 1-3 字，无数字/地址字/字段关键字
                    if (next.length() >= 1 && next.length() <= 3
                            && next.matches("[\\u4e00-\\u9fff]+")
                            && !addrPattern.matcher(next).find()
                            && !next.contains("公") && !next.contains("号")
                            && !next.contains("码") && !next.contains("出")
                            && !next.contains("性") && !next.contains("民")
                            && !next.contains("住") && !next.contains("地")) {
                        nameBuf.append(next);
                    }
                }
                break;
            }
        }
        card.name = nameBuf.toString().trim();

        // ─── 住址：收集所有地址特征行 ───
        // 身份证地址常见格式："住址湖北省..." + 续行
        // 不论 OCR 顺序如何，收拢所有包含地址特征的行
        StringBuilder addrBuf = new StringBuilder();
        boolean addrStarted = false;
        for (String line : lines) {
            if (line.contains("住址")) {
                addrStarted = true;
                String after = line.replaceAll(".*住址\\s*", "").trim();
                if (!after.isEmpty()) addrBuf.append(after);
            } else if (addrStarted) {
                // 一旦遇到明确的非地址行即停止
                if (line.contains("公民") || line.contains("号码") || line.contains("姓名")
                        || line.contains("出生") || line.contains("性别") || line.contains("民族")) {
                    // 但仍需检查后续是否有地址续行
                    break;
                }
            }
        }

        // 补充：地址后半段可能出现在姓名之后（OCR 空间顺序导致），
        // 检查姓名行之后是否有地址特征行
        int nameLineIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("姓名")) {
                nameLineIdx = i;
                break;
            }
        }
        if (nameLineIdx >= 0) {
            for (int i = nameLineIdx + 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                // 跳过已经是姓名一部分的行
                if (card.name.contains(line) || line.equals(card.name)) continue;
                // 以地址关键字结尾或包含地址特征
                if (addrPattern.matcher(line).find()
                        && !line.contains("公民")
                        && !line.contains("身份证")
                        && !line.contains("号码") ) {
                    addrBuf.append(line);
                }
            }
        }
        card.address = addrBuf.toString().trim();

        return card;
    }

    // ============================================================
    // 业务调用示例
    // ============================================================

    public static void testWithFile() {
        OcrResult r = ocrChinese(new File("/data/image.jpg"));
        System.out.println(r.getText());
    }

    public static void testWithUrl() {
        OcrResult r = ocrChineseByUrl("http://127.0.0.1:9000/wuliu/idcard/xxx.jpg");
        System.out.println(r.getText());

        // 身份证解析
        IdCardResult card = parseIdCard(r);
        System.out.println(card);
    }

    public static void testWithMultipartFile(MultipartFile file) throws IOException {
        OcrResult r = ocrChinese(file);
        System.out.println(r.getText());
    }

    // ============================================================
    // 你原有的 AES 工具方法
    // ============================================================

    public static void main(String[] args) {
        testWithUrl();
    }

    public void test() {
        // 原有 AES 方法
    }
}
