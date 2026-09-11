package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.ai.entity.AiTaskRecord;
import com.maou.apptemplateapi.module.ai.service.AiClient;
import com.maou.apptemplateapi.module.ai.service.AiTaskService;
import com.maou.apptemplateapi.module.ai.service.StructuredAiResult;
import com.maou.apptemplateapi.module.ai.service.StructuredAiService;
import com.maou.apptemplateapi.module.ai.service.StructuredAiService.StructuredAiSpec;
import com.maou.apptemplateapi.module.literature.dto.*;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import com.maou.apptemplateapi.module.literature.entity.LiteratureQaRecord;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureChunkMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureQaRecordMapper;
import com.maou.apptemplateapi.module.rag.dto.RagChunkResponse;
import com.maou.apptemplateapi.module.rag.service.RagRerankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureAiService {

    private static final String BIZ_TYPE_LITERATURE = "LITERATURE";
    private static final String TASK_SUMMARY = "LITERATURE_SUMMARY";
    private static final String TASK_QUESTION = "LITERATURE_QUESTION";
    private static final String TASK_COMPARE = "LITERATURE_COMPARE";
    private static final String TASK_SEARCH_PARSE = "LITERATURE_SEARCH_PARSE";

    private final AiClient aiClient;
    private final AiTaskService aiTaskService;
    private final StructuredAiService structuredAiService;
    private final LiteratureMapper literatureMapper;
    private final LiteratureChunkMapper chunkMapper;
    private final LiteratureQaRecordMapper qaRecordMapper;
    private final LiteratureService literatureService;
    private final LiteratureIndexService literatureIndexService;
    private final RagRerankService ragRerankService;
    private final ObjectMapper objectMapper;

    @Transactional
    public LiteratureSummaryResponse generateSummary(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "literature-summary");
        String source = literatureIndexService.buildIndexContent(literature);
        try {
            StructuredAiResult<StructuredLiteratureSummary> result = structuredAiService.generate(new StructuredAiSpec<>(
                    currentUser.getOrganizationId(),
                    TASK_SUMMARY,
                    BIZ_TYPE_LITERATURE,
                    literatureId,
                    "literature_summary_v1",
                    summarySystemPrompt(),
                    summaryUserPrompt(source),
                    summarySchemaPrompt(),
                    StructuredLiteratureSummary.class,
                    this::validateSummary,
                    () -> fallbackSummary(literature)
            ));
            String summary = writeStructuredJson(result.value(), TASK_SUMMARY, literatureId, result.taskId());
            literature.setAiSummary(summary);
            literature.setSummaryStatus(result.fallbackUsed() ? "FALLBACK" : "SUCCESS");
            literature.setSummaryUpdatedAt(LocalDateTime.now());
            literatureMapper.updateById(literature);
            log.info("literature summary generated, scenario=literature-summary, literatureId={}, userId={}, taskId={}, attemptCount={}, repaired={}, fallbackUsed={}",
                    literatureId, currentUser.getId(), result.taskId(), result.attemptCount(), result.repaired(), result.fallbackUsed());
            return new LiteratureSummaryResponse(literatureId, summary, literature.getSummaryStatus(), literature.getSummaryUpdatedAt());
        } catch (RuntimeException exception) {
            literature.setSummaryStatus("FAILED");
            literatureMapper.updateById(literature);
            log.error("literature summary failed, scenario=literature-summary, literatureId={}, userId={}",
                    literatureId, currentUser.getId(), exception);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    public LiteratureSearchResponse semanticSearch(LiteratureSearchRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        List<LiteratureReferenceChunkResponse> references = searchChunks(request.query(), request.categoryId(), request.limit(), "literature-semantic-search");
        List<Long> literatureIds = references.stream().map(LiteratureReferenceChunkResponse::literatureId).distinct().toList();
        List<LiteratureResponse> literatures = literatureIds.isEmpty()
                ? List.of()
                : literatureMapper.selectBatchIds(literatureIds).stream()
                .filter(item -> isReadable(item, currentUser))
                .map(item -> literatureService.toResponse(item, null))
                .toList();
        return new LiteratureSearchResponse(request.query(), literatures, references, null);
    }

    public LiteratureSearchResponse naturalLanguageSearch(LiteratureSearchRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        String parsedIntent;
        String semanticQuery;
        try {
            StructuredAiResult<StructuredSearchIntent> result = structuredAiService.generate(new StructuredAiSpec<>(
                    currentUser.getOrganizationId(),
                    TASK_SEARCH_PARSE,
                    "SEARCH",
                    currentUser.getId(),
                    "literature_search_intent_v1",
                    searchSystemPrompt(),
                    searchUserPrompt(request.query()),
                    searchSchemaPrompt(),
                    StructuredSearchIntent.class,
                    this::validateSearchIntent,
                    () -> fallbackSearchIntent(request.query())
            ));
            parsedIntent = writeStructuredJson(result.value(), TASK_SEARCH_PARSE, currentUser.getId(), result.taskId());
            semanticQuery = toSearchQuery(result.value());
            log.info("literature natural search intent parsed, scenario=literature-natural-search, userId={}, taskId={}, attemptCount={}, repaired={}, fallbackUsed={}, query={}",
                    currentUser.getId(), result.taskId(), result.attemptCount(), result.repaired(), result.fallbackUsed(), request.query());
        } catch (RuntimeException exception) {
            StructuredSearchIntent fallback = fallbackSearchIntent(request.query());
            parsedIntent = writeStructuredJson(fallback, TASK_SEARCH_PARSE, currentUser.getId(), null);
            semanticQuery = toSearchQuery(fallback);
            log.error("literature natural search parse unexpected failed, scenario=literature-natural-search, userId={}, query={}, fallbackUsed=true",
                    currentUser.getId(), request.query(), exception);
        }
        LiteratureSearchResponse semantic = semanticSearch(new LiteratureSearchRequest(semanticQuery, request.categoryId(), request.limit()));
        return new LiteratureSearchResponse(request.query(), semantic.literatures(), semantic.references(), parsedIntent);
    }

    @Transactional
    public LiteratureQuestionResponse answer(Long literatureId, LiteratureQuestionRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "literature-question");
        List<LiteratureReferenceChunkResponse> references = searchChunks(request.question(), literature.getCategoryId(), request.limit(), "literature-question");
        references = references.stream().filter(item -> literatureId.equals(item.literatureId())).toList();
        if (references.isEmpty()) {
            references = literatureIndexService.listEnabledChunks(literatureId).stream()
                    .limit(Math.max(request.limit() == null ? 5 : request.limit(), 1))
                    .map(chunk -> new LiteratureReferenceChunkResponse(chunk.getId(), chunk.getLiteratureId(), chunk.getChunkIndex(), chunk.getChunkText(), 0))
                    .toList();
        }
        String referenceText = references.stream()
                .map(item -> "[chunk-%s] %s".formatted(item.chunkIndex(), item.content()))
                .collect(Collectors.joining("\n\n"));
        AiTaskRecord task = aiTaskService.createPending(currentUser.getOrganizationId(), TASK_QUESTION, BIZ_TYPE_LITERATURE, literatureId,
                request.question() + "\n\n" + referenceText);
        long started = System.currentTimeMillis();
        try {
            String answer = aiClient.generate(questionSystemPrompt(), """
                    Literature title: %s
                    Question: %s

                    Reference chunks:
                    %s
                    """.formatted(literature.getTitle(), request.question(), truncatePrompt(referenceText)));
            LiteratureQaRecord record = new LiteratureQaRecord();
            record.setUserId(currentUser.getId());
            record.setLiteratureId(literatureId);
            record.setQuestion(request.question());
            record.setAnswer(answer);
            record.setReferenceChunks(writeJson(references));
            record.setStatus("SUCCESS");
            qaRecordMapper.insert(record);
            aiTaskService.markSuccess(task.getId(), answer, System.currentTimeMillis() - started);
            log.info("literature question answered, scenario=literature-question, literatureId={}, userId={}, taskId={}, recordId={}, referenceCount={}",
                    literatureId, currentUser.getId(), task.getId(), record.getId(), references.size());
            return new LiteratureQuestionResponse(record.getId(), literatureId, request.question(), answer, references, record.getCreatedAt());
        } catch (RuntimeException exception) {
            aiTaskService.markFailed(task.getId(), exception.getMessage(), System.currentTimeMillis() - started);
            LiteratureQaRecord record = new LiteratureQaRecord();
            record.setUserId(currentUser.getId());
            record.setLiteratureId(literatureId);
            record.setQuestion(request.question());
            record.setAnswer("");
            record.setReferenceChunks(writeJson(references));
            record.setStatus("FAILED");
            record.setErrorMessage(exception.getMessage());
            qaRecordMapper.insert(record);
            log.error("literature question failed, scenario=literature-question, literatureId={}, userId={}, taskId={}, recordId={}",
                    literatureId, currentUser.getId(), task.getId(), record.getId(), exception);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    public LiteratureCompareResponse compare(LiteratureCompareRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        List<Literature> literatures = request.literatureIds().stream()
                .map(id -> literatureService.requireReadableLiterature(id, currentUser, "literature-compare"))
                .toList();
        String input = literatures.stream()
                .map(item -> "ID: %s\nTitle: %s\nAbstract: %s\nAI Summary: %s\nKeywords: %s"
                        .formatted(item.getId(), item.getTitle(), value(item.getAbstractText()), value(item.getAiSummary()), value(item.getKeywords())))
                .collect(Collectors.joining("\n\n---\n\n"));
        AiTaskRecord task = aiTaskService.createPending(currentUser.getOrganizationId(), TASK_COMPARE, "LITERATURE_COMPARE", currentUser.getId(), input);
        long started = System.currentTimeMillis();
        try {
            String analysis = aiClient.generate(compareSystemPrompt(), truncatePrompt(input));
            aiTaskService.markSuccess(task.getId(), analysis, System.currentTimeMillis() - started);
            log.info("literature compared, scenario=literature-compare, userId={}, taskId={}, literatureIds={}",
                    currentUser.getId(), task.getId(), request.literatureIds());
            return new LiteratureCompareResponse(request.literatureIds(), analysis);
        } catch (RuntimeException exception) {
            aiTaskService.markFailed(task.getId(), exception.getMessage(), System.currentTimeMillis() - started);
            log.error("literature compare failed, scenario=literature-compare, userId={}, taskId={}, literatureIds={}",
                    currentUser.getId(), task.getId(), request.literatureIds(), exception);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    public List<LiteratureQuestionResponse> qaHistory(Long literatureId, long page, long size) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (literatureId != null) {
            literatureService.requireReadableLiterature(literatureId, currentUser, "literature-qa-history");
        }
        Page<LiteratureQaRecord> result = qaRecordMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<LiteratureQaRecord>()
                        .eq(LiteratureQaRecord::getUserId, currentUser.getId())
                        .eq(literatureId != null, LiteratureQaRecord::getLiteratureId, literatureId)
                        .eq(LiteratureQaRecord::getDeleted, 0)
                        .orderByDesc(LiteratureQaRecord::getCreatedAt)
                        .orderByDesc(LiteratureQaRecord::getId));
        return result.getRecords().stream()
                .map(record -> new LiteratureQuestionResponse(record.getId(), record.getLiteratureId(), record.getQuestion(), record.getAnswer(), readReferences(record.getReferenceChunks()), record.getCreatedAt()))
                .toList();
    }

    private List<LiteratureReferenceChunkResponse> searchChunks(String query, Long categoryId, Integer limit, String scenario) {
        CurrentUser currentUser = CurrentUserProvider.require();
        int requestedLimit = Math.max(limit == null ? 6 : limit, 1);
        List<LiteratureChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<LiteratureChunk>()
                .eq(LiteratureChunk::getEnabled, 1)
                .eq(LiteratureChunk::getDeleted, 0));
        if (chunks.isEmpty()) {
            return List.of();
        }
        Map<Long, Literature> literatureMap = literatureMapper.selectBatchIds(chunks.stream()
                        .map(LiteratureChunk::getLiteratureId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Literature::getId, item -> item));
        Set<String> tokens = normalizeTokens(query);
        List<RagChunkResponse> candidates = chunks.stream()
                .filter(chunk -> {
                    Literature literature = literatureMap.get(chunk.getLiteratureId());
                    if (literature == null) {
                        return false;
                    }
                    if (categoryId != null && !categoryId.equals(literature.getCategoryId())) {
                        return false;
                    }
                    return isReadable(literature, currentUser);
                })
                .map(chunk -> new RagChunkResponse(chunk.getId(), chunk.getLiteratureId(), "literature", chunk.getChunkText(), score(chunk.getChunkText(), tokens)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(RagChunkResponse::score).reversed())
                .limit(Math.max(requestedLimit, 12))
                .toList();
        return ragRerankService.rerank(query, candidates, scenario).stream()
                .limit(requestedLimit)
                .map(item -> new LiteratureReferenceChunkResponse(item.id(), item.documentId(), null, item.content(), item.score()))
                .toList();
    }

    private boolean isReadable(Literature literature, CurrentUser currentUser) {
        try {
            literatureService.requireReadableLiterature(literature.getId(), currentUser, "literature-ai-readable-check");
            return true;
        } catch (BusinessException exception) {
            log.warn("literature filtered by permission, scenario=literature-ai-readable-check, literatureId={}, userId={}, errorCode={}",
                    literature.getId(), currentUser.getId(), exception.getErrorCode());
            return false;
        }
    }

    private Set<String> tokenize(String query) {
        Set<String> tokens = new HashSet<>();
        if (!StringUtils.hasText(query)) {
            return tokens;
        }
        Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,.;，。；、]+"))
                .filter(StringUtils::hasText)
                .forEach(tokens::add);
        for (int i = 0; i < query.length() - 1; i++) {
            tokens.add(query.substring(i, i + 2).toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private double score(String content, Set<String> tokens) {
        String normalized = content.toLowerCase(Locale.ROOT);
        return tokens.stream().filter(normalized::contains).count();
    }

    private String truncatePrompt(String text) {
        int max = aiTaskService.maxPromptChars();
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.error("literature reference json write failed, scenario=literature-reference-json, valueType={}",
                    value == null ? null : value.getClass().getName(), exception);
            return "[]";
        }
    }

    private String writeStructuredJson(Object value, String taskType, Long bizId, Long taskId) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.error("structured ai business json write failed, scenario=structured-ai-business-json, taskType={}, bizId={}, taskId={}, valueType={}",
                    taskType, bizId, taskId, value == null ? null : value.getClass().getName(), exception);
            return "{}";
        }
    }

    private List<LiteratureReferenceChunkResponse> readReferences(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(LiteratureReferenceChunkResponse.class).readValue(json);
        } catch (RuntimeException | java.io.IOException exception) {
            log.error("literature reference json parse failed, scenario=literature-reference-json, json={}", json, exception);
            return List.of();
        }
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private String summarySystemPrompt() {
        return "You are an academic reading assistant. Return only one JSON object. The JSON must match the schema and validation rules. Use Chinese. Base every field only on the supplied literature content. Do not invent facts.";
    }

    private String questionSystemPrompt() {
        return "You are an academic literature QA assistant. Answer only from the supplied reference chunks. If the chunks are insufficient, say that the current literature content is insufficient. Mention chunk indexes when useful.";
    }

    private String compareSystemPrompt() {
        return "Compare the selected academic literatures in Chinese. Cover research topic, method, conclusion, differences, limitations, and follow-up research suggestions. Do not invent missing details.";
    }

    private String searchSystemPrompt() {
        return "You convert a user's natural language academic search request into one JSON object. Return only JSON. Follow the schema and validation rules exactly. Do not add markdown.";
    }

    private String summaryUserPrompt(String source) {
        return """
                Required schema:
                %s

                Literature content:
                %s
                """.formatted(summarySchemaPrompt(), truncatePrompt(source));
    }

    private String summarySchemaPrompt() {
        return """
                {
                  "background": "string, required, 20-200 Chinese characters",
                  "problem": "string, required, 20-200 Chinese characters",
                  "method": "string, required, 20-240 Chinese characters",
                  "conclusion": "string, required, 20-240 Chinese characters",
                  "innovation": "string, required, 10-180 Chinese characters; say evidence is insufficient if unknown",
                  "keywords": ["1-10 short academic keywords, no duplicates"],
                  "suitableReaders": "string, required, 10-160 Chinese characters"
                }
                """;
    }

    private String searchUserPrompt(String query) {
        return """
                Required schema:
                %s

                User query:
                %s
                """.formatted(searchSchemaPrompt(), query);
    }

    private String searchSchemaPrompt() {
        return """
                {
                  "keywords": ["1-8 concise academic search terms extracted from the user query"],
                  "language": "zh|en|mixed|unknown",
                  "startYear": "integer from 1900 to current year + 1, or null",
                  "endYear": "integer from 1900 to current year + 1, or null",
                  "sort": "relevance_desc|year_desc|read_count_desc"
                }
                Validation: keywords must not be empty; if both years exist startYear <= endYear; use relevance_desc when the user does not request sorting.
                """;
    }

    private void validateSummary(StructuredLiteratureSummary summary) {
        if (summary == null) {
            throw new IllegalArgumentException("summary is null");
        }
        requireText(summary.background(), "background");
        requireText(summary.problem(), "problem");
        requireText(summary.method(), "method");
        requireText(summary.conclusion(), "conclusion");
        requireText(summary.innovation(), "innovation");
        requireText(summary.suitableReaders(), "suitableReaders");
        validateKeywordList(summary.keywords(), 10, "keywords");
    }

    private void validateSearchIntent(StructuredSearchIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("search intent is null");
        }
        validateKeywordList(intent.keywords(), 8, "keywords");
        if (StringUtils.hasText(intent.language()) && !Set.of("zh", "en", "mixed", "unknown").contains(intent.language())) {
            throw new IllegalArgumentException("language must be zh, en, mixed, or unknown");
        }
        int maxYear = LocalDateTime.now().getYear() + 1;
        validateYear(intent.startYear(), maxYear, "startYear");
        validateYear(intent.endYear(), maxYear, "endYear");
        if (intent.startYear() != null && intent.endYear() != null && intent.startYear() > intent.endYear()) {
            throw new IllegalArgumentException("startYear must be less than or equal to endYear");
        }
        if (!Set.of("relevance_desc", "year_desc", "read_count_desc").contains(intent.sort())) {
            throw new IllegalArgumentException("sort is invalid");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is blank");
        }
    }

    private void validateKeywordList(List<String> keywords, int maxSize, String field) {
        if (keywords == null || keywords.isEmpty()) {
            throw new IllegalArgumentException(field + " is empty");
        }
        if (keywords.size() > maxSize) {
            throw new IllegalArgumentException(field + " size exceeds " + maxSize);
        }
        long validCount = keywords.stream().filter(StringUtils::hasText).distinct().count();
        if (validCount != keywords.size()) {
            throw new IllegalArgumentException(field + " contains blank or duplicate values");
        }
    }

    private void validateYear(Integer year, int maxYear, String field) {
        if (year != null && (year < 1900 || year > maxYear)) {
            throw new IllegalArgumentException(field + " out of range");
        }
    }

    private StructuredLiteratureSummary fallbackSummary(Literature literature) {
        String title = StringUtils.hasText(literature.getTitle()) ? literature.getTitle() : "未命名文献";
        String abstractText = StringUtils.hasText(literature.getAbstractText())
                ? literature.getAbstractText()
                : "当前文献缺少可用摘要，系统只能基于题名、作者、来源和关键词生成保守说明。";
        List<String> keywords = fallbackKeywords(literature.getKeywords(), title);
        String source = StringUtils.hasText(literature.getSource()) ? literature.getSource() : "未记录来源";
        return new StructuredLiteratureSummary(
                "本文献题名为《%s》，来源为%s，系统基于现有元数据提供保守背景说明。".formatted(title, source),
                "当前可见内容显示其主要问题与题名和摘要相关，细节仍需结合原文进一步核验。",
                "现有材料未稳定给出完整研究方法，建议后续通过原文解析或人工补录完善方法描述。",
                "基于摘要可初步判断：%s".formatted(truncateText(abstractText, 180)),
                "现有证据不足以确认明确创新点，暂按题名、摘要与关键词作为后续阅读线索。",
                keywords,
                "适合需要快速了解该文献主题、摘要与后续精读价值的学生或研究者。"
        );
    }

    private StructuredSearchIntent fallbackSearchIntent(String query) {
        List<String> keywords = Arrays.stream(query == null ? new String[0] : query.split("[\\s,.;,，。；、]+"))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
        if (keywords.isEmpty() && StringUtils.hasText(query)) {
            keywords = List.of(truncateText(query, 40));
        }
        if (keywords.isEmpty()) {
            keywords = List.of("学术文献");
        }
        return new StructuredSearchIntent(keywords, "unknown", null, null, "relevance_desc");
    }

    private List<String> fallbackKeywords(String keywords, String title) {
        List<String> values = StringUtils.hasText(keywords)
                ? Arrays.stream(keywords.split("[,，;；、\\s]+"))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(10)
                .toList()
                : List.of();
        if (!values.isEmpty()) {
            return values;
        }
        return List.of(truncateText(title, 30));
    }

    private String toSearchQuery(StructuredSearchIntent intent) {
        return intent.keywords().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(" "));
    }

    private Set<String> normalizeTokens(String query) {
        Set<String> tokens = new HashSet<>();
        if (!StringUtils.hasText(query)) {
            return tokens;
        }
        Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,.;,，。；、]+"))
                .filter(StringUtils::hasText)
                .forEach(tokens::add);
        for (int i = 0; i < query.length() - 1; i++) {
            tokens.add(query.substring(i, i + 2).toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
