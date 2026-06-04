package com.yt.ocr.model;

import lombok.Data;

/**
 * 车牌颜色
 */
@Data
public class PlateColor {
    /** 颜色类型: blue/yellow/green/white/black */
    private String type;
    /** 中文名: 蓝牌/黄牌/绿牌/白牌/黑牌 */
    private String name;
    /** 说明: 小型汽车/新能源汽车等 */
    private String desc;
    /** 置信度 0~1 */
    private double confidence;
}
