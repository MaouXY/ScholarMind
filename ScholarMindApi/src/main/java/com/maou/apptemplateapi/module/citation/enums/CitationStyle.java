package com.maou.apptemplateapi.module.citation.enums;

import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;

import java.util.Arrays;

/**
 * 支持的参考文献引用格式。
 */
public enum CitationStyle {

    GB_T_7714("GB/T 7714", "GB/T 7714（中国国家标准）"),
    APA("APA", "APA 第 7 版"),
    MLA("MLA", "MLA 第 9 版");

    private final String code;
    private final String label;

    CitationStyle(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CitationStyle fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.CITATION_STYLE_INVALID);
        }
        return Arrays.stream(values())
                .filter(style -> style.code.equalsIgnoreCase(code.trim()) || style.name().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CITATION_STYLE_INVALID));
    }
}
