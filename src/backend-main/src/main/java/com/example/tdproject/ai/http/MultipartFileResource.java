package com.example.tdproject.ai.http;

import org.springframework.core.io.ByteArrayResource;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 让 RestTemplate 发送 multipart/form-data 时保留原始文件名。
 */
public class MultipartFileResource extends ByteArrayResource {

    private final String filename;

    public MultipartFileResource(MultipartFile multipartFile) {
        super(toBytes(multipartFile));
        this.filename = multipartFile.getOriginalFilename();
    }

    @Override
    public String getFilename() {
        return this.filename;
    }

    private static byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败: " + e.getMessage(), e);
        }
    }
}
