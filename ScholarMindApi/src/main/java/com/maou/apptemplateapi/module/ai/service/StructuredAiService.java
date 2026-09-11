package com.maou.apptemplateapi.module.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maou.apptemplateapi.module.ai.entity.AiTaskRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredAiService {

    private final AiClient aiClient;
    private final AiTaskService aiTaskService;
    private final ObjectMapper objectMapper;

    public <T> StructuredAiResult<T> generate(StructuredAiSpec<T> spec) {
        AiTaskRecord task = aiTaskService.createPending(
                spec.organizationId(),
                spec.taskType(),
                spec.bizType(),
                spec.bizId(),
                spec.userPrompt()
        );
        long started = System.currentTimeMillis();
        String firstOutput = null;
        try {
            firstOutput = aiClient.generateJson(spec.systemPrompt(), spec.userPrompt());
            T parsed = parseAndValidate(firstOutput, spec.responseType(), spec.validator());
            aiTaskService.markSuccess(task.getId(), firstOutput, System.currentTimeMillis() - started);
            log.info("structured ai output valid, scenario=structured-ai-output, taskType={}, bizType={}, bizId={}, taskId={}, schemaName={}, attemptCount=1, repaired=false, fallbackUsed=false",
                    spec.taskType(), spec.bizType(), spec.bizId(), task.getId(), spec.schemaName());
            return new StructuredAiResult<>(parsed, firstOutput, task.getId(), 1, false, false);
        } catch (RuntimeException exception) {
            log.error("structured ai output invalid, scenario=structured-ai-output, taskType={}, bizType={}, bizId={}, taskId={}, schemaName={}, attempt=1, output={}",
                    spec.taskType(), spec.bizType(), spec.bizId(), task.getId(), spec.schemaName(), snapshot(firstOutput), exception);
            return repairOrFallback(spec, task, started, firstOutput, exception);
        }
    }

    private <T> StructuredAiResult<T> repairOrFallback(StructuredAiSpec<T> spec,
                                                       AiTaskRecord task,
                                                       long started,
                                                       String invalidOutput,
                                                       RuntimeException firstException) {
        String repairOutput = null;
        try {
            repairOutput = aiClient.generateJson(repairSystemPrompt(), repairUserPrompt(spec, invalidOutput, firstException));
            T parsed = parseAndValidate(repairOutput, spec.responseType(), spec.validator());
            aiTaskService.markSuccess(task.getId(), repairOutput, System.currentTimeMillis() - started);
            log.warn("structured ai output repaired, scenario=structured-ai-repair, taskType={}, bizType={}, bizId={}, taskId={}, schemaName={}, attemptCount=2, repaired=true, fallbackUsed=false",
                    spec.taskType(), spec.bizType(), spec.bizId(), task.getId(), spec.schemaName());
            return new StructuredAiResult<>(parsed, repairOutput, task.getId(), 2, true, false);
        } catch (RuntimeException repairException) {
            log.error("structured ai repair failed, scenario=structured-ai-repair, taskType={}, bizType={}, bizId={}, taskId={}, schemaName={}, attempt=2, output={}",
                    spec.taskType(), spec.bizType(), spec.bizId(), task.getId(), spec.schemaName(), snapshot(repairOutput), repairException);
            T fallback = spec.fallbackSupplier().get();
            aiTaskService.markFailed(task.getId(), repairException.getMessage(), System.currentTimeMillis() - started);
            log.warn("structured ai fallback used, scenario=structured-ai-fallback, taskType={}, bizType={}, bizId={}, taskId={}, schemaName={}, attemptCount=2, repaired=false, fallbackUsed=true, firstError={}, repairError={}",
                    spec.taskType(), spec.bizType(), spec.bizId(), task.getId(), spec.schemaName(),
                    firstException.getClass().getSimpleName(), repairException.getClass().getSimpleName());
            return new StructuredAiResult<>(fallback, writeFallbackJson(fallback, spec), task.getId(), 2, false, true);
        }
    }

    private <T> T parseAndValidate(String output, Class<T> responseType, Consumer<T> validator) {
        if (!StringUtils.hasText(output)) {
            throw new IllegalArgumentException("AI output is blank");
        }
        try {
            T parsed = objectMapper.readValue(extractJson(output), responseType);
            validator.accept(parsed);
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI output is not valid JSON: " + exception.getOriginalMessage(), exception);
        }
    }

    private String extractJson(String output) {
        String trimmed = output.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String repairSystemPrompt() {
        return "You repair invalid AI structured output. Return only one valid JSON object that matches the required schema. No markdown.";
    }

    private <T> String repairUserPrompt(StructuredAiSpec<T> spec, String invalidOutput, RuntimeException exception) {
        return """
                Schema name: %s
                Required JSON schema and validation rules:
                %s

                Original user input:
                %s

                Invalid output:
                %s

                Validation or parse error:
                %s
                """.formatted(
                spec.schemaName(),
                spec.schemaPrompt(),
                spec.userPrompt(),
                invalidOutput == null ? "" : invalidOutput,
                exception.getMessage()
        );
    }

    private <T> String writeFallbackJson(T fallback, StructuredAiSpec<T> spec) {
        try {
            return objectMapper.writeValueAsString(fallback);
        } catch (JsonProcessingException exception) {
            log.error("structured ai fallback json write failed, scenario=structured-ai-fallback-json, taskType={}, bizType={}, bizId={}, schemaName={}",
                    spec.taskType(), spec.bizType(), spec.bizId(), spec.schemaName(), exception);
            return "{}";
        }
    }

    private String snapshot(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    public record StructuredAiSpec<T>(
            Long organizationId,
            String taskType,
            String bizType,
            Long bizId,
            String schemaName,
            String systemPrompt,
            String userPrompt,
            String schemaPrompt,
            Class<T> responseType,
            Consumer<T> validator,
            Supplier<T> fallbackSupplier
    ) {
    }
}
