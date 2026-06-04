package com.yt.ocr.model;

import lombok.Data;
import java.util.List;

@Data
public class StructureResult {
    private boolean success;
    private int count;
    private List<StructureItem> items;
}
