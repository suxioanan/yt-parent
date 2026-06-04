package com.yt.ocr.service.impl;

import com.yt.ocr.model.OcrItem;
import com.yt.ocr.model.OcrResult;
import com.yt.ocr.client.OcrClient;
import com.yt.ocr.config.OcrProperties;
import com.yt.ocr.model.IdCardResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 身份证是被
 * @author sunan
 * @date 2026/5/28
 **/
public class IdCardService extends OcrChineseService {

    public IdCardService(OcrClient client, OcrProperties properties) {
        super(client, properties);
    }

    // ============================================================
    // 身份证解析
    // ============================================================

    public IdCardResult parseIdCard(OcrResult ocrResult) {
        IdCardResult card = new IdCardResult();
        if (ocrResult == null || ocrResult.getItems() == null) return card;

        List<String> lines = new ArrayList<>();
        for (OcrItem item : ocrResult.getItems()) {
            lines.add(item.getText().trim());
        }

        String allText = String.join("", lines);

        Pattern idPattern = Pattern.compile("[1-9]\\d{16}[0-9Xx]");
        Matcher m = idPattern.matcher(allText);
        if (m.find()) {
            card.setIdNumber(m.group().toUpperCase());
        }

        if (card.getIdNumber() != null && card.getIdNumber().length() >= 14) {
            card.setBirthDate(card.getIdNumber().substring(6, 10) + "-"
                    + card.getIdNumber().substring(10, 12) + "-"
                    + card.getIdNumber().substring(12, 14));
        }

        for (String line : lines) {
            if (line.contains("男")) { card.setGender("男"); break; }
            if (line.contains("女")) { card.setGender("女"); break; }
        }

        for (String line : lines) {
            if (line.contains("民族")) {
                card.setEthnicity(line.replaceAll(".*民族\\s*", "").trim());
                break;
            }
        }

        String addrKeywords = "村|组|号|镇|乡|县|市|区|路|街|巷|弄|栋|单元|室|楼|层";
        Pattern addrPattern = Pattern.compile(addrKeywords);

        StringBuilder nameBuf = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("姓名")) {
                nameBuf.append(line.replaceAll(".*姓名\\s*", "").trim());
                for (int j = i + 1; j < Math.min(i + 5, lines.size()); j++) {
                    String next = lines.get(j).trim();
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
        card.setName(nameBuf.toString().trim());

        StringBuilder addrBuf = new StringBuilder();
        boolean addrStarted = false;
        for (String line : lines) {
            if (line.contains("住址")) {
                addrStarted = true;
                String after = line.replaceAll(".*住址\\s*", "").trim();
                if (!after.isEmpty()) addrBuf.append(after);
            } else if (addrStarted) {
                if (line.contains("公民") || line.contains("号码") || line.contains("姓名")
                        || line.contains("出生") || line.contains("性别") || line.contains("民族")) {
                    break;
                }
            }
        }

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
                if (card.getName().contains(line) || line.equals(card.getName())) continue;
                if (addrPattern.matcher(line).find()
                        && !line.contains("公民")
                        && !line.contains("身份证")
                        && !line.contains("号码")) {
                    addrBuf.append(line);
                }
            }
        }
        card.setAddress(addrBuf.toString().trim());

        return card;
    }

    // ============================================================
    // 便捷方法：直接传图片，返回解析好的身份证信息
    // ============================================================

    public IdCardResult parseIdCard(File file) {
        return parseIdCard(ocr(file));
    }

    public IdCardResult parseIdCard(MultipartFile file) throws IOException {
        return parseIdCard(ocr(file));
    }

    public IdCardResult parseIdCardByUrl(String imageUrl) {
        return parseIdCard(ocrByUrl(imageUrl));
    }

    public IdCardResult parseIdCardByPath(String imagePath) {
        return parseIdCard(ocrByPath(imagePath));
    }
}
