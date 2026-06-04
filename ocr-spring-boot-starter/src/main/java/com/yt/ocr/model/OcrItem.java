package com.yt.ocr.model;

import lombok.Data;

@Data
public class OcrItem {
    private String text;
    private double confidence;
    private double[] box;
}
