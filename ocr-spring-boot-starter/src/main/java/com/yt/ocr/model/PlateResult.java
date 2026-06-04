package com.yt.ocr.model;

import lombok.Data;

import java.util.List;

/**
 * 车牌识别结构化结果
 */
@Data
public class PlateResult {
    /** 识别是否成功 */
    private boolean success;
    /** 完整车牌号码 (e.g. 京A12345) */
    private String plateNumber;
    /** 省份简称 (e.g. 京/沪/粤) */
    private String province;
    /** 城市代号 (e.g. A/B/N) */
    private String cityCode;
    /** 是否新能源车牌 */
    private boolean isNewEnergy;
    /** 车牌类型: 蓝牌/黄牌/绿牌/白牌/黑牌 */
    private String plateType;
    /** 识别置信度 (首个车牌 item 的 confidence) */
    private double confidence;
    /** 原始识别文本 */
    private String rawText;
    /** 车牌颜色信息（API 返回） */
    private PlateColor plateColor;
    /** OCR 识别明细（每个检测框） */
    private List<OcrItem> items;
}
