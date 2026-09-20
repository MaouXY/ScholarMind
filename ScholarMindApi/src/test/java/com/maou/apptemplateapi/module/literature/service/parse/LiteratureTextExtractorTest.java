package com.maou.apptemplateapi.module.literature.service.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 文献正文抽取器单元测试：现场用 PDFBox / POI 生成 PDF 与 docx，不联网、不依赖 MySQL。
 */
class LiteratureTextExtractorTest {

    private static final String SCENARIO = "literature-text-extract-test";

    /** Windows / Linux 常见中文字体候选，用于验证 PDFBox 能抽取中文 */
    private static final List<String> CJK_FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/msyh.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");

    private final LiteratureTextExtractor extractor = new LiteratureTextExtractor();

    @Test
    void extractsChineseAndEnglishTextFromGeneratedPdf() throws Exception {
        byte[] pdf = buildPdf(List.of(
                "ScholarMind 文献检索与阅读助手",
                "Hybrid Retrieval for Academic Search"));

        LiteratureTextExtractor.ExtractionResult result =
                extractor.extract("application/pdf", "paper.pdf", pdf, SCENARIO);

        assertTrue(result.supported(), "application/pdf 必须在支持范围内");
        assertTrue(result.text().contains("Hybrid Retrieval for Academic Search"),
                "英文关键字应被抽取出来，实际文本=" + result.text());
        assertTrue(result.text().contains("ScholarMind"), "英文关键字 ScholarMind 应被抽取出来");
        // 中文字形依赖系统字体：抽不到中文时跳过中文断言（无 CJK 字体的 CI 环境）
        assumeTrue(result.text().contains("文献检索"), "当前环境无法渲染中文 PDF 字形，跳过中文断言");
        assertTrue(result.text().contains("文献检索与阅读助手"), "中文关键字应被抽取出来");
    }

    @Test
    void extractsParagraphTextFromGeneratedDocx() throws Exception {
        byte[] docx = buildDocx(List.of(
                "面向高校图书馆的混合检索方法研究",
                "本文提出一种结合稀疏 BM25 与稠密向量的混合召回策略。",
                "实验表明该策略显著提升了检索准确率。"));

        LiteratureTextExtractor.ExtractionResult result = extractor.extract(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "paper.docx", docx, SCENARIO);

        assertTrue(result.supported(), "docx 必须在支持范围内");
        assertTrue(result.text().contains("混合召回策略"), "段落内容应被抽取出来，实际文本=" + result.text());
        assertTrue(result.text().contains("检索准确率"), "后续段落内容应被抽取出来");
        assertTrue(result.text().contains("\n"), "段落之间应保留换行");
    }

    @Test
    void extractsDocxByExtensionWhenContentTypeIsMissing() throws Exception {
        byte[] docx = buildDocx(List.of("Extension Fallback Title", "Second paragraph content"));

        LiteratureTextExtractor.ExtractionResult result = extractor.extract(null, "fallback.DOCX", docx, SCENARIO);

        assertTrue(result.supported());
        assertTrue(result.text().contains("Extension Fallback Title"));
        assertTrue(result.text().contains("Second paragraph content"));
    }

    @Test
    void decodesPlainTextAsUtf8WithBom() {
        byte[] payload = concat(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "文献标题：AI 图书馆检索助手\r\n\r\n\r\n\r\n摘要：本文研究混合检索。".getBytes(StandardCharsets.UTF_8));

        LiteratureTextExtractor.ExtractionResult result = extractor.extract("text/plain", "note.txt", payload, SCENARIO);

        assertTrue(result.supported());
        assertNull(result.reason(), "正常抽取不应带失败原因");
        assertFalse(result.text().startsWith("\uFEFF"), "BOM 必须被去掉");
        assertTrue(result.text().contains("文献标题：AI 图书馆检索助手"));
        assertTrue(result.text().contains("摘要：本文研究混合检索。"));
    }

    @Test
    void decodesMarkdownContentType() {
        byte[] payload = "# 标题\n\n\n\n- 中文条目\n- second item".getBytes(StandardCharsets.UTF_8);

        LiteratureTextExtractor.ExtractionResult result = extractor.extract("text/markdown", "readme.md", payload, SCENARIO);

        assertTrue(result.supported());
        assertTrue(result.text().contains("# 标题"));
        assertTrue(result.text().contains("second item"));
    }

    @Test
    void reportsUnsupportedContentType() {
        LiteratureTextExtractor.ExtractionResult result = extractor.extract(
                "image/png", "figure.png", new byte[]{1, 2, 3}, SCENARIO);

        assertFalse(result.supported(), "图片类型不支持正文抽取");
        assertEquals("", result.text());
        assertNotNull(result.reason());
        assertTrue(result.reason().contains("不在正文抽取支持范围"), "reason 应说明类型不支持，实际=" + result.reason());
    }

    @Test
    void reportsLegacyDocAsUnsupported() {
        LiteratureTextExtractor.ExtractionResult result = extractor.extract(
                "application/msword", "legacy.doc", "legacy binary content".getBytes(StandardCharsets.UTF_8), SCENARIO);

        assertFalse(result.supported(), "老 .doc 二进制格式当前不支持，应走题录兜底");
        assertEquals("", result.text());
    }

    @Test
    void returnsEmptyTextForEmptyBytes() {
        LiteratureTextExtractor.ExtractionResult result =
                extractor.extract("application/pdf", "empty.pdf", new byte[0], SCENARIO);

        assertTrue(result.supported(), "空字节属于「类型支持但解析为空」");
        assertEquals("", result.text());
        assertNotNull(result.reason());
    }

    @Test
    void returnsEmptyTextAndReasonForBlankPdf() throws Exception {
        byte[] pdf = buildPdf(List.of("   ", ""));

        LiteratureTextExtractor.ExtractionResult result =
                extractor.extract("application/pdf", "blank.pdf", pdf, SCENARIO);

        assertTrue(result.supported(), "解析过程本身没有异常，只是没有文本");
        assertEquals("", result.text());
        assertNotNull(result.reason());
        assertTrue(result.reason().contains("空文本"), "reason 应为空文本摘要，实际=" + result.reason());
    }

    @Test
    void returnsEmptyTextWhenPdfBytesAreCorrupted() {
        byte[] corrupted = "%PDF-1.7 broken pdf bytes".getBytes(StandardCharsets.UTF_8);

        LiteratureTextExtractor.ExtractionResult result =
                extractor.extract("application/pdf", "broken.pdf", corrupted, SCENARIO);

        assertTrue(result.supported(), "解析异常仍视为类型支持，由调用方兜底");
        assertEquals("", result.text());
        assertNotNull(result.reason(), "异常必须给出摘要，供 parse_error 写回");
    }

    @Test
    void normalizesLineBreaksAndCompressesBlankLines() {
        String raw = "\uFEFF  第一段\r\n\r\n\r\n\r\n第二段\r第三段   ";

        String normalized = LiteratureTextExtractor.normalize(raw);

        assertEquals("第一段\n\n第二段\n第三段", normalized);
    }

    @Test
    void buildsFallbackReasonFromUnsupportedResult() {
        LiteratureTextExtractor.ExtractionResult result =
                extractor.extract("image/gif", "a.gif", new byte[]{1}, SCENARIO);

        String reason = LiteratureTextExtractor.fallbackReason(result);

        assertTrue(reason.contains("未解析到正文"), "兜底文案应说明未解析到正文，实际=" + reason);
        assertTrue(reason.contains("已使用题录与摘要兜底"), "兜底文案应说明走了题录摘要，实际=" + reason);
    }

    private byte[] buildPdf(List<String> lines) throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDFont font = loadCjkFont(document);
                contentStream.beginText();
                contentStream.setFont(font, 14);
                contentStream.newLineAtOffset(60, 700);
                contentStream.setLeading(20f);
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLine();
                }
                contentStream.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            bytes = output.toByteArray();
        }
        return bytes;
    }

    /**
     * 优先加载系统中文字体（同时能渲染中英文），找不到就退化为 PDFBox 内置 Helvetica（仅英文）。
     */
    private PDFont loadCjkFont(PDDocument document) throws Exception {
        for (String candidate : CJK_FONT_CANDIDATES) {
            File file = new File(candidate);
            if (file.isFile()) {
                try {
                    return PDType0Font.load(document, file);
                } catch (Exception ignored) {
                    // 该字体加载失败则尝试下一个候选
                }
            }
        }
        return new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
    }

    private byte[] buildDocx(List<String> paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(text);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] concat(byte[] head, byte[] tail) {
        byte[] merged = new byte[head.length + tail.length];
        System.arraycopy(head, 0, merged, 0, head.length);
        System.arraycopy(tail, 0, merged, head.length, tail.length);
        return merged;
    }
}
