package com.maou.apptemplateapi.module.citation.service;

import com.maou.apptemplateapi.module.citation.enums.CitationStyle;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 参考文献格式化：依据文献元数据生成 GB/T 7714、APA 7、MLA 9 三种引用文本。
 *
 * 受元数据限制的简化说明（论文可据此说明实现边界）：
 * 1. 缺少出版地、卷期与页码字段，因此 GB/T 7714 采用「作者. 题名[J]. 来源, 年.」的期刊条目简化形式；
 * 2. 来源缺失时按电子资源标注 [EB/OL]；
 * 3. 年份缺失时 GB/T 7714 标注「出版年不详」，APA/MLA 标注 n.d.；
 * 4. 作者姓名按分隔符切分；APA/MLA 对「Li Ming」这类西文姓名做「Li, M.」缩写，中文姓名保持原样。
 */
@Component
public class CitationFormatter {

    private static final String UNKNOWN_TITLE = "未命名文献";
    private static final String UNKNOWN_YEAR_GB = "出版年不详";
    private static final String UNKNOWN_YEAR_LATIN = "n.d.";
    public String format(Literature literature, CitationStyle style) {
        return switch (style) {
            case GB_T_7714 -> formatGbT7714(literature);
            case APA -> formatApa(literature);
            case MLA -> formatMla(literature);
        };
    }

    public List<String> parseAuthors(String author) {
        if (!StringUtils.hasText(author)) {
            return List.of();
        }
        List<String> authors = new ArrayList<>();
        for (String part : author.split("[;；,，、/]+|\\band\\b")) {
            String value = part.trim();
            if (StringUtils.hasText(value) && !authors.contains(value)) {
                authors.add(value);
            }
        }
        return authors;
    }

    private String formatGbT7714(Literature literature) {
        String authors = String.join(", ", parseAuthors(literature.getAuthor()));
        String title = title(literature);
        String source = StringUtils.hasText(literature.getSource()) ? literature.getSource().trim() : null;
        String year = literature.getPublishYear() == null ? UNKNOWN_YEAR_GB : String.valueOf(literature.getPublishYear());
        String typeMark = source == null ? "[EB/OL]" : "[J]";

        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(authors)) {
            builder.append(authors).append(". ");
        }
        builder.append(title).append(typeMark).append(". ");
        if (source != null) {
            builder.append(source).append(", ");
        }
        builder.append(year).append('.');
        return builder.toString();
    }

    private String formatApa(Literature literature) {
        boolean surnameFirst = surnameFirst(literature);
        String authors = apaAuthors(parseAuthors(literature.getAuthor()), surnameFirst);
        String year = literature.getPublishYear() == null ? "(n.d.)." : "(" + literature.getPublishYear() + ").";
        String title = title(literature);
        String source = StringUtils.hasText(literature.getSource()) ? literature.getSource().trim() : null;

        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(authors)) {
            builder.append(authors).append(' ');
        }
        builder.append(year).append(' ').append(title).append('.');
        if (source != null) {
            builder.append(' ').append(source).append('.');
        }
        return builder.toString();
    }

    private String formatMla(Literature literature) {
        List<String> authors = parseAuthors(literature.getAuthor());
        boolean surnameFirst = surnameFirst(literature);
        String title = title(literature);
        String source = StringUtils.hasText(literature.getSource()) ? literature.getSource().trim() : null;
        String year = literature.getPublishYear() == null ? "n.d." : literature.getPublishYear() + ".";

        StringBuilder builder = new StringBuilder();
        if (!authors.isEmpty()) {
            builder.append(mlaAuthors(authors, surnameFirst)).append(". ");
        }
        builder.append('"').append(title).append(".\"");
        if (source != null) {
            builder.append(' ').append(source).append(',');
        }
        builder.append(' ').append(year);
        return builder.toString();
    }

    /**
     * 姓名书写顺序判定：中文文献（language 缺省或 zh 开头）按「姓 名」处理，西文文献按「名 姓」处理。
     */
    private boolean surnameFirst(Literature literature) {
        String language = literature.getLanguage();
        return language == null || language.isBlank() || language.toLowerCase().startsWith("zh");
    }

    private String apaAuthors(List<String> authors, boolean surnameFirst) {
        if (authors.isEmpty()) {
            return "";
        }
        List<String> formatted = authors.stream().map(author -> apaName(author, surnameFirst)).toList();
        if (formatted.size() == 1) {
            return formatted.get(0);
        }
        String head = String.join(", ", formatted.subList(0, formatted.size() - 1));
        return head + ", & " + formatted.get(formatted.size() - 1);
    }

    private String mlaAuthors(List<String> authors, boolean surnameFirst) {
        if (authors.size() == 1) {
            return mlaName(authors.get(0), surnameFirst);
        }
        if (authors.size() == 2) {
            return mlaName(authors.get(0), surnameFirst) + ", and " + authors.get(1);
        }
        return mlaName(authors.get(0), surnameFirst) + ", et al";
    }

    /** 西文姓名缩写：Li Ming -> Li, M.；中文或单词姓名原样返回。 */
    private String apaName(String name, boolean surnameFirst) {
        List<String> parts = splitName(name);
        if (parts.size() < 2) {
            return name;
        }
        int surnameIndex = surnameFirst ? 0 : parts.size() - 1;
        String surname = parts.get(surnameIndex);
        String initials = parts.stream()
                .filter(part -> !part.equals(surname))
                .map(part -> part.substring(0, 1).toUpperCase() + ".")
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return surname + ", " + initials;
    }

    /** MLA 首位作者写法：Li Ming -> Li, Ming；中文姓名原样返回。 */
    private String mlaName(String name, boolean surnameFirst) {
        List<String> parts = splitName(name);
        if (parts.size() < 2) {
            return name;
        }
        int surnameIndex = surnameFirst ? 0 : parts.size() - 1;
        String surname = parts.get(surnameIndex);
        String given = parts.stream()
                .filter(part -> !part.equals(surname))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return surname + ", " + given;
    }

    private List<String> splitName(String name) {
        if (!StringUtils.hasText(name)) {
            return List.of();
        }
        boolean ascii = name.chars().allMatch(code -> code < 128);
        if (!ascii) {
            return List.of(name.trim());
        }
        List<String> parts = Arrays.stream(name.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .toList();
        return parts.size() >= 2 ? parts : List.of(name.trim());
    }

    private String title(Literature literature) {
        return StringUtils.hasText(literature.getTitle()) ? literature.getTitle().trim() : UNKNOWN_TITLE;
    }
}
