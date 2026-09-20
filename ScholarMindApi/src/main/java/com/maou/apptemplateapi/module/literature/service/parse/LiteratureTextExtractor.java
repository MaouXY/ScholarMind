package com.maou.apptemplateapi.module.literature.service.parse;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文献正文抽取器：把绑定到对象存储的原始文件字节（PDF / docx / 纯文本）解析成可供分块索引的正文。
 *
 * <p>设计约束：
 * <ul>
 *   <li>无状态、不依赖 Spring 上下文，只做纯函数式解析，方便单元测试直接 new 出来调用；</li>
 *   <li>解析异常一律不抛出：返回 {@code text=""} 并把异常摘要写进 {@code reason}，由调用方决定是否兜底；</li>
 *   <li>返回值 {@code supported=false} 仅表示「文件类型不在支持范围内」，此时调用方应直接用题录摘要兜底。</li>
 * </ul>
 */
@Slf4j
@Component
public class LiteratureTextExtractor {

    /** 抽取场景名，统一用于结构化日志 */
    public static final String SCENARIO = "literature-text-extract";

    public static final String CONTENT_TYPE_PDF = "application/pdf";
    public static final String CONTENT_TYPE_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String CONTENT_TYPE_DOC = "application/msword";
    public static final String CONTENT_TYPE_MARKDOWN = "text/markdown";

    private static final String TYPE_LABEL_UNSUPPORTED = "UNSUPPORTED";

    private static final String REASON_UNSUPPORTED = "文件类型不在正文抽取支持范围内";
    private static final String REASON_EMPTY_BYTES = "文件内容为空";
    private static final String REASON_EMPTY_TEXT = "解析结果为空文本";

    /**
     * 抽取结果。
     *
     * @param text      抽取到的正文；可为空串（表示没有可用正文）
     * @param supported 文件类型是否在支持范围内
     * @param reason    说明：类型不支持的原因、或解析异常摘要；成功时为 null
     */
    public record ExtractionResult(String text, boolean supported, String reason) {

        public static ExtractionResult success(String text) {
            return new ExtractionResult(text, true, null);
        }

        public static ExtractionResult unsupported(String reason) {
            return new ExtractionResult("", false, reason);
        }

        public static ExtractionResult failure(String reason) {
            return new ExtractionResult("", true, reason);
        }

        /** 是否拿到了可用于索引的正文 */
        public boolean hasText() {
            return StringUtils.hasText(text);
        }
    }

    /**
     * 按 contentType / 扩展名分派到具体解析器，任何异常都被吞掉并降级为「解析失败但类型支持」。
     */
    public ExtractionResult extract(String contentType, String originalName, byte[] bytes, String scenario) {
        String normalizedScenario = StringUtils.hasText(scenario) ? scenario : SCENARIO;
        String typeLabel = resolveTypeLabel(contentType, originalName);
        if (bytes == null || bytes.length == 0) {
            log.warn("literature text extract skipped, scenario={}, contentType={}, originalName={}, bytes=0, type={}, reason={}",
                    normalizedScenario, contentType, originalName, typeLabel, REASON_EMPTY_BYTES);
            return ExtractionResult.failure(REASON_EMPTY_BYTES);
        }
        ExtractionResult unsupported = ExtractionResult.unsupported(REASON_UNSUPPORTED);
        try {
            ExtractionResult result = switch (typeLabel) {
                case "PDF" -> successOrEmpty(extractPdf(bytes));
                case "DOCX" -> successOrEmpty(extractDocx(bytes));
                case "TEXT" -> successOrEmpty(extractPlainText(bytes));
                default -> unsupported;
            };
            if (!result.supported()) {
                log.info("literature text extract unsupported, scenario={}, contentType={}, originalName={}, bytes={}, type={}, reason={}",
                        normalizedScenario, contentType, originalName, bytes.length, typeLabel, result.reason());
                return result;
            }
            log.info("literature text extracted, scenario={}, contentType={}, originalName={}, bytes={}, type={}, textChars={}, supported={}",
                    normalizedScenario, contentType, originalName, bytes.length, typeLabel, result.text().length(), true);
            if (result.reason() != null) {
                log.warn("literature text extract degraded, scenario={}, contentType={}, originalName={}, bytes={}, type={}, reason={}",
                        normalizedScenario, contentType, originalName, bytes.length, typeLabel, result.reason());
            }
            return result;
        } catch (Exception exception) {
            String reason = summarize(exception);
            log.error("literature text extract failed, scenario={}, contentType={}, originalName={}, bytes={}, type={}",
                    normalizedScenario, contentType, originalName, bytes.length, typeLabel, exception);
            return ExtractionResult.failure(reason);
        }
    }

    /**
     * 供索引服务写回 {@code literature.parse_error} 的兜底原因文案。
     *
     * <p>约定：类型不支持、解析为空、或上层（如 S3 读取）传入的失败摘要，统一拼成一句中文说明。
     */
    public static String fallbackReason(ExtractionResult result) {
        String detail = REASON_UNSUPPORTED;
        if (result != null && StringUtils.hasText(result.reason())) {
            detail = result.reason();
        } else if (result != null && result.supported()) {
            detail = REASON_EMPTY_TEXT;
        }
        return "未解析到正文（%s），已使用题录与摘要兜底".formatted(detail);
    }

    /** PDF：PDFBox 3.x 通过 Loader.loadPDF(byte[]) 在内存中加载，再用 PDFTextStripper 抽取文本。 */
    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 清洗依赖段落结构：保持默认的分页换行行为，由 normalize 统一收口空行
            return normalize(stripper.getText(document));
        }
    }

    /** docx：POI XWPFDocument 按段落抽取，段落之间保留换行，作为分块的天然边界。 */
    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<String> paragraphs = new ArrayList<>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (StringUtils.hasText(text)) {
                    paragraphs.add(text.trim());
                }
            }
            return normalize(String.join("\n", paragraphs));
        }
    }

    /** 纯文本：优先严格 UTF-8 解码，遇到非法字节序列再回退 ISO-8859-1，保证不丢内容。 */
    private String extractPlainText(byte[] bytes) {
        String text;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (Exception exception) {
            log.warn("literature text extract utf-8 decode failed, scenario={}, bytes={}, fallbackCharset=ISO-8859-1, reason={}",
                    SCENARIO, bytes.length, summarize(exception));
            text = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        return normalize(stripBom(text));
    }

    /**
     * 文本清理：去掉 BOM 与首尾空白、统一换行符为 \n、压缩 3 个以上连续空行，同时保留段落换行。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = stripBom(raw)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        // 只压缩空行，不动单换行：段落结构是后续分块的依据
        text = text.replaceAll("\n[ \t]*\n([ \t]*\n)+", "\n\n");
        return text.trim();
    }

    private static String stripBom(String text) {
        if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    private ExtractionResult successOrEmpty(String text) {
        return StringUtils.hasText(text) ? ExtractionResult.success(text) : ExtractionResult.failure(REASON_EMPTY_TEXT);
    }

    /** 按 contentType 优先、扩展名兜底判断实际解析器类型。 */
    private String resolveTypeLabel(String contentType, String originalName) {
        String normalizedType = contentType == null ? "" : contentType.trim().toLowerCase();
        if (normalizedType.startsWith("text/plain") || CONTENT_TYPE_MARKDOWN.equals(normalizedType)) {
            return "TEXT";
        }
        if (CONTENT_TYPE_PDF.equals(normalizedType)) {
            return "PDF";
        }
        if (CONTENT_TYPE_DOCX.equals(normalizedType)) {
            return "DOCX";
        }
        if (CONTENT_TYPE_DOC.equals(normalizedType)) {
            // 老的二进制 .doc 不被 POI 的 XWPF 支持，且 HWPF 依赖较重在当前场景收益低 → 走题录兜底
            return TYPE_LABEL_UNSUPPORTED;
        }
        String extension = extensionOf(originalName);
        return switch (extension) {
            case ".pdf" -> "PDF";
            case ".docx" -> "DOCX";
            case ".txt", ".md" -> "TEXT";
            default -> TYPE_LABEL_UNSUPPORTED;
        };
    }

    private String extensionOf(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return "";
        }
        String name = originalName.trim().toLowerCase();
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index);
    }

    private static String summarize(Exception exception) {
        String message = exception.getMessage();
        String type = exception.getClass().getSimpleName();
        return StringUtils.hasText(message) ? "%s: %s".formatted(type, message) : type;
    }
}
