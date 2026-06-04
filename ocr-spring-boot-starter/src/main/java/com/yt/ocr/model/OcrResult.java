package com.yt.ocr.model;

import lombok.Data;
import java.util.List;

@Data
public class OcrResult {
    private boolean success;
    private String text;
    private int count;
    private List<OcrItem> items;

}
