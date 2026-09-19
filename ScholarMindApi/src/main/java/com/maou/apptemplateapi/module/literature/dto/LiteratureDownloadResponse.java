package com.maou.apptemplateapi.module.literature.dto;

/**
 * 文献下载响应：返回 RustFS 预签名地址，前端可直接打开完成下载。
 */
public record LiteratureDownloadResponse(
        Long literatureId,
        String fileName,
        String contentType,
        Long sizeBytes,
        String url,
        Long expiresInSeconds,
        Integer downloadCount
) {
}
