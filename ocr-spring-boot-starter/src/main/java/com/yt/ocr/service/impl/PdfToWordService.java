package com.yt.ocr.service.impl;

import com.yt.ocr.client.OcrClient;
import com.yt.ocr.config.OcrProperties;
import com.yt.ocr.service.OcrOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * PDF 转换为 Word 文档（.docx）
 * <p>
 * 调用远程 PaddleOCR 服务的 {@code /convert/pdf-to-docx} 接口，
 * 返回 Word 文件字节流。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 通过文件路径
 * byte[] docx = pdfToWordService.ocrByPath("/path/to/document.pdf");
 *
 * // 通过 MultipartFile
 * byte[] docx = pdfToWordService.ocr(file);
 *
 * // 保存为文件
 * Files.write(Paths.get("/path/to/output.docx"), docx);
 * }</pre>
 *
 * @author sunan
 * @date 2026/7/15
 */
public class PdfToWordService implements OcrOperations<byte[]> {

    private final OcrClient client;
    private final OcrProperties properties;

    private String getEndpoint() {
        return properties.getEndpoints().getPdftoword();
    }

    public PdfToWordService(OcrClient client, OcrProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 将 PDF 文件转换为 Word
     *
     * @param file PDF 文件
     * @return Word 文件（.docx）的字节数组
     */
    @Override
    public byte[] ocr(File file) {
        return client.postFileForBytes(getEndpoint(), file);
    }

    /**
     * 将上传的 PDF 文件转换为 Word
     *
     * @param file MultipartFile（.pdf）
     * @return Word 文件（.docx）的字节数组
     * @throws IOException 文件读取异常
     */
    @Override
    public byte[] ocr(MultipartFile file) throws IOException {
        return client.postMultipartFileForBytes(getEndpoint(), file);
    }

    /**
     * 通过文件路径将 PDF 转换为 Word
     *
     * @param apth PDF 文件路径
     * @return Word 文件（.docx）的字节数组
     */
    @Override
    public byte[] ocrByPath(String apth) {
        return ocr(new File(apth));
    }

    /**
     * 通过 URL 地址将 PDF 文档转换为 Word
     * <p>
     * 服务端会自行下载并转换，返回 Word 字节
     *
     * @param url PDF 文档的 HTTP/HTTPS URL
     * @return Word 文件（.docx）的字节数组
     */
    @Override
    public byte[] ocrByUrl(String url) {
        return client.postUrlForBytes(getEndpoint(), url);
    }
}
