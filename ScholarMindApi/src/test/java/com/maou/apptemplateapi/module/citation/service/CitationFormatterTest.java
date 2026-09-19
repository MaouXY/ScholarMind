package com.maou.apptemplateapi.module.citation.service;

import com.maou.apptemplateapi.module.citation.enums.CitationStyle;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 引用格式化单元测试：覆盖三种格式的正常输入与元数据缺失的兜底表现。
 */
class CitationFormatterTest {

    private final CitationFormatter formatter = new CitationFormatter();

    @Test
    void formatsChineseLiteratureInThreeStyles() {
        Literature literature = literature(
                "基于关键词与用户行为的高校数字图书馆混合推荐研究",
                "李明, 张伟",
                "图书情报工作",
                2024,
                "digital library; hybrid recommendation");

        assertEquals("李明, 张伟. 基于关键词与用户行为的高校数字图书馆混合推荐研究[J]. 图书情报工作, 2024.",
                formatter.format(literature, CitationStyle.GB_T_7714));
        assertEquals("李明, & 张伟 (2024). 基于关键词与用户行为的高校数字图书馆混合推荐研究. 图书情报工作.",
                formatter.format(literature, CitationStyle.APA));
        assertEquals("李明, and 张伟. \"基于关键词与用户行为的高校数字图书馆混合推荐研究.\" 图书情报工作, 2024.",
                formatter.format(literature, CitationStyle.MLA));
    }

    @Test
    void formatsWesternNamesWithInitials() {
        Literature literature = literature(
                "Hybrid Retrieval for Academic Search",
                "Li Ming; Wang Xiaohong",
                "Journal of Information Science",
                2023,
                "hybrid retrieval");

        assertEquals("Li, M., & Wang, X. (2023). Hybrid Retrieval for Academic Search. Journal of Information Science.",
                formatter.format(literature, CitationStyle.APA));
        assertEquals("Li, Ming, and Wang Xiaohong. \"Hybrid Retrieval for Academic Search.\" Journal of Information Science, 2023.",
                formatter.format(literature, CitationStyle.MLA));
        assertEquals("Li Ming, Wang Xiaohong. Hybrid Retrieval for Academic Search[J]. Journal of Information Science, 2023.",
                formatter.format(literature, CitationStyle.GB_T_7714));
    }

    @Test
    void fallsBackWhenMetadataMissing() {
        Literature literature = new Literature();
        literature.setTitle("未标注来源的文献");

        assertEquals("未标注来源的文献[EB/OL]. 出版年不详.", formatter.format(literature, CitationStyle.GB_T_7714));
        assertEquals("(n.d.). 未标注来源的文献.", formatter.format(literature, CitationStyle.APA));
        assertEquals("\"未标注来源的文献.\" n.d.", formatter.format(literature, CitationStyle.MLA));
    }

    @Test
    void splitsAndDeduplicatesAuthors() {
        assertEquals(3, formatter.parseAuthors("李明、张伟, 王芳;李明").size());
        assertEquals("李明", formatter.parseAuthors("李明、张伟, 王芳;李明").get(0));
        assertEquals(0, formatter.parseAuthors("   ").size());
    }

    private Literature literature(String title, String author, String source, Integer year, String keywords) {
        Literature literature = new Literature();
        literature.setTitle(title);
        literature.setAuthor(author);
        literature.setSource(source);
        literature.setPublishYear(year);
        literature.setKeywords(keywords);
        return literature;
    }
}
