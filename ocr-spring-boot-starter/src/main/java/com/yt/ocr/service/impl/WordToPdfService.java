package com.yt.ocr.service.impl;

import com.yt.ocr.client.OcrClient;
import com.yt.ocr.config.OcrProperties;
import com.yt.ocr.service.OcrOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * Word 文档（.doc / .docx）转换为 PDF
 * <p>
 * 调用远程 PaddleOCR 服务的 {@code /convert/docx-to-pdf} 接口，
 * 返回 PDF 文件字节流。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 通过文件路径
 * byte[] pdf = wordToPdfService.ocrByPath("/path/to/doc.docx");
 *
 * // 通过 MultipartFile
 * byte[] pdf = wordToPdfService.ocr(file);
 *
 * // 保存为文件
 * Files.write(Paths.get("/path/to/output.pdf"), pdf);
 * }</pre>
 *
 * @author sunan
 * @date 2026/5/28
 */
public class WordToPdfService implements OcrOperations<byte[]> {

    private final OcrClient client;
    private final OcrProperties properties;

    private String getEndpoint() {
        return properties.getEndpoints().getWordtopdf();
    }

    public WordToPdfService(OcrClient client, OcrProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 将 Word 文件转换为 PDF
     *
     * @param file Word 文件（.doc 或 .docx）
     * @return PDF 文件的字节数组
     */
    @Override
    public byte[] ocr(File file) {
        return client.postFileForBytes(getEndpoint(), file);
    }

    /**
     * 将上传的 Word 文件转换为 PDF
     *
     * @param file MultipartFile（.doc 或 .docx）
     * @return PDF 文件的字节数组
     * @throws IOException 文件读取异常
     */
    @Override
    public byte[] ocr(MultipartFile file) throws IOException {
        return client.postMultipartFileForBytes(getEndpoint(), file);
    }

    /**
     * 通过文件路径将 Word 转换为 PDF
     *
     * @param imagePath Word 文件路径
     * @return PDF 文件的字节数组
     */
    @Override
    public byte[] ocrByPath(String imagePath) {
        return ocr(new File(imagePath));
    }

    /**
     * 通过 URL 地址将 Word 文档转换为 PDF
     * <p>
     * 服务端会自行下载并转换，返回 PDF 字节
     *
     * @param imageUrl Word 文档的 HTTP/HTTPS URL
     * @return PDF 文件的字节数组
     */
    @Override
    public byte[] ocrByUrl(String imageUrl) {
        return client.postUrlForBytes(getEndpoint(), imageUrl);
    }
}
