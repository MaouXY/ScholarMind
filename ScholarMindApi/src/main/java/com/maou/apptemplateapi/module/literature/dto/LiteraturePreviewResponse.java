package com.maou.apptemplateapi.module.literature.dto;

/**
 * 文献原文预览响应：与下载票据同构，但**不累加下载次数**。
 * 阅读页用它在 iframe 里内联渲染原文（PDF 走浏览器内置阅读器、text/plain 走纯文本视图）。
 */
public record LiteraturePreviewResponse(
        Long literatureId,
        String fileName,
        String contentType,
        Long sizeBytes,
        String url,
        Long expiresInSeconds,
        /** 浏览器能否内联渲染：pdf / text 为 true，docx 等为 false，前端据此回落到下载入口 */
        boolean inlineViewable
) {
}
