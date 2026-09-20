package com.maou.apptemplateapi.module.literature.service.retrieval;

import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BM25 稀疏检索。
 *
 * 设计要点：
 * 1. 中文没有天然分词，这里采用「英文/数字词 + 中文字符二元组」的轻量切分，避免引入分词依赖，
 *    同时保证专有名词、型号、人名等精确匹配（这正是纯向量召回容易失手的场景）；
 * 2. 标准 BM25 公式：score = Σ idf(t) * tf*(k1+1) / (tf + k1*(1 - b + b*dl/avgdl))，
 *    idf 采用加 1 平滑的 Robertson-Sparck Jones 形式，避免词频为 0 时的负值；
 * 3. 只输出得分大于 0 的分块，并按得分降序给出名次，供 RRF 融合使用。
 */
@Component
public class SparseRetriever {

    private static final Pattern LATIN_WORD = Pattern.compile("[a-z0-9]+");
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fa5]+");

    public List<ScoredChunk> rank(String query,
                                  List<LiteratureChunk> chunks,
                                  int topK,
                                  double k1,
                                  double b) {
        if (!StringUtils.hasText(query) || chunks == null || chunks.isEmpty() || topK <= 0) {
            return List.of();
        }
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        Map<Long, Map<String, Integer>> termFrequencies = new LinkedHashMap<>();
        Map<String, Integer> documentFrequencies = new HashMap<>();
        double totalLength = 0;
        for (LiteratureChunk chunk : chunks) {
            List<String> tokens = tokenize(chunk.getChunkText());
            totalLength += tokens.size();
            Map<String, Integer> frequencies = new HashMap<>();
            for (String token : tokens) {
                frequencies.merge(token, 1, Integer::sum);
            }
            termFrequencies.put(chunk.getId(), frequencies);
            for (String term : frequencies.keySet()) {
                documentFrequencies.merge(term, 1, Integer::sum);
            }
        }
        double averageLength = chunks.isEmpty() ? 1 : Math.max(totalLength / chunks.size(), 1);

        List<ScoredChunk> scored = new ArrayList<>();
        for (LiteratureChunk chunk : chunks) {
            Map<String, Integer> frequencies = termFrequencies.get(chunk.getId());
            int documentLength = frequencies.values().stream().mapToInt(Integer::intValue).sum();
            double score = 0;
            for (String term : queryTerms) {
                Integer tf = frequencies.get(term);
                if (tf == null || tf == 0) {
                    continue;
                }
                int df = documentFrequencies.getOrDefault(term, 0);
                double idf = Math.log(1 + (chunks.size() - df + 0.5) / (df + 0.5));
                double denominator = tf + k1 * (1 - b + b * documentLength / averageLength);
                score += idf * (tf * (k1 + 1)) / denominator;
            }
            if (score > 0) {
                scored.add(new ScoredChunk(chunk.getId(), score, 0));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(ScoredChunk::chunkId));
        List<ScoredChunk> ranked = new ArrayList<>();
        for (int i = 0; i < scored.size() && i < topK; i++) {
            ScoredChunk item = scored.get(i);
            ranked.add(new ScoredChunk(item.chunkId(), item.score(), i + 1));
        }
        return ranked;
    }

    /** 关键词切分：英文/数字按词切，中文按字符二元组切（单字保留）。 */
    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT);

        Matcher latin = LATIN_WORD.matcher(normalized);
        while (latin.find()) {
            String word = latin.group();
            if (word.length() >= 2) {
                tokens.add(word);
            }
        }

        Matcher cjk = CJK_RUN.matcher(normalized);
        while (cjk.find()) {
            String run = cjk.group();
            if (run.length() == 1) {
                tokens.add(run);
                continue;
            }
            for (int i = 0; i < run.length() - 1; i++) {
                tokens.add(run.substring(i, i + 2));
            }
        }
        return tokens;
    }

    public record ScoredChunk(Long chunkId, double score, int rank) {
    }
}
