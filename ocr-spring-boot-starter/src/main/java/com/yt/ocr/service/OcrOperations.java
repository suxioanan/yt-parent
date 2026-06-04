package com.yt.ocr.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

public interface OcrOperations<T> {

    T ocr(File file);

    T ocr(MultipartFile file) throws IOException;

    T ocrByUrl(String imageUrl);

    T ocrByPath(String imagePath);
}
