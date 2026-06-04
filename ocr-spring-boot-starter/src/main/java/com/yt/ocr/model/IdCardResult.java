package com.yt.ocr.model;

import lombok.Data;

@Data
public class IdCardResult {
    private String name;
    private String idNumber;
    private String address;
    private String gender;
    private String ethnicity;
    private String birthDate;
}
