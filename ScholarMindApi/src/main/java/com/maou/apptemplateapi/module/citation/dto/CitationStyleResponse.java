package com.maou.apptemplateapi.module.citation.dto;

/**
 * 某篇文献在某格式下的引用文本（即时生成，不落库）。
 */
public record CitationStyleResponse(
        String style,
        String styleLabel,
        String text
) {
}
