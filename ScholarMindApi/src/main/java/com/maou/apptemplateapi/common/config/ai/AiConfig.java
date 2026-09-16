package com.maou.apptemplateapi.common.config.ai;

import com.maou.apptemplateapi.module.ai.service.AiClient;
import com.maou.apptemplateapi.module.ai.service.AiImageInput;
import dev.langchain4j.data.message.*;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
@Slf4j
public class AiConfig {

    @Bean
    public AiClient aiClient(AiProperties properties) {
        if (!properties.enabled()) {
            String reason = "app.ai.ark.enabled=false";
            if (properties.failFast()) {
                throw new IllegalStateException("AI fallback client activated while fail-fast is enabled: " + reason);
            }
            log.warn("AI fallback client activated: {}", reason);
            return unavailableClient(reason);
        }

        if (isBlank(properties.apiKey())) {
            String reason = "API key is blank";
            if (properties.failFast()) {
                throw new IllegalStateException("AI fallback client activated while fail-fast is enabled: " + reason);
            }
            log.warn("AI fallback client activated: {}", reason);
            return unavailableClient(reason);
        }

        if (isBlank(properties.modelName())) {
            String reason = "model name is blank";
            if (properties.failFast()) {
                throw new IllegalStateException("AI fallback client activated while fail-fast is enabled: " + reason);
            }
            log.warn("AI fallback client activated: {}", reason);
            return unavailableClient(reason);
        }

        log.info(
                "AI ChatModel initializing, provider=ark, enabled={}, baseUrl={}, modelName={}, timeoutSeconds={}, temperature={}",
                properties.enabled(),
                properties.baseUrl(),
                properties.modelName(),
                properties.timeoutSeconds(),
                properties.temperature()
        );

        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds() == null ? 20 : properties.timeoutSeconds());
        double temperature = properties.temperature() == null ? 0.7 : properties.temperature();

        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .modelName(properties.modelName())
                .timeout(timeout)
                .temperature(temperature)
                // 关闭 SDK 内置重试，重试策略统一由 LangChain4jAiClientImpl 控制，避免两层重试相乘
                .maxRetries(0)
                .build();

        ChatModel jsonChatModel = OpenAiChatModel.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .modelName(properties.modelName())
                .timeout(timeout)
                .temperature(temperature)
                .responseFormat(ResponseFormat.JSON)
                .maxRetries(0)
                .build();

        log.info("AI real client activated: LangChain4jAiClient");
        return new LangChain4jAiClientImpl(chatModel, jsonChatModel);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AiClient unavailableClient(String reason) {
        return new AiClient() {
            @Override
            public String generate(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("AI client unavailable: " + reason);
            }

            @Override
            public String generateJson(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("AI client unavailable: " + reason);
            }

            @Override
            public String generateWithImages(String systemPrompt, String userPrompt, List<AiImageInput> images) {
                throw new IllegalStateException("AI client unavailable: " + reason);
            }
        };
    }

    private static final class LangChain4jAiClientImpl implements AiClient {

        /** 单次业务调用允许的最大尝试次数（含首次），仅在可重试异常上生效。 */
        private static final int MAX_ATTEMPTS = 3;
        /** 退避基数：第 n 次重试前等待 n * BASE_BACKOFF_MS。 */
        private static final long BASE_BACKOFF_MS = 800L;

        private final ChatModel chatModel;
        private final ChatModel jsonChatModel;

        private LangChain4jAiClientImpl(ChatModel chatModel, ChatModel jsonChatModel) {
            this.chatModel = chatModel;
            this.jsonChatModel = jsonChatModel;
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            String mergedPrompt = merge(systemPrompt, userPrompt);
            return withRetry("generate", () -> chatModel.chat(mergedPrompt));
        }

        @Override
        public String generateJson(String systemPrompt, String userPrompt) {
            String mergedPrompt = merge(systemPrompt, userPrompt);
            return withRetry("generateJson", () -> jsonChatModel.chat(mergedPrompt));
        }

        @Override
        public String generateWithImages(String systemPrompt, String userPrompt, List<AiImageInput> images) {
            List<Content> contents = new ArrayList<>();
            contents.add(TextContent.from(userPrompt));
            if (images != null) {
                for (AiImageInput image : images) {
                    String base64 = Base64.getEncoder().encodeToString(image.bytes());
                    contents.add(ImageContent.from(base64, image.contentType()));
                }
            }
            return withRetry("generateWithImages", () -> chatModel.chat(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(contents)
            ).aiMessage().text());
        }

        private String merge(String systemPrompt, String userPrompt) {
            return systemPrompt + System.lineSeparator() + System.lineSeparator() + userPrompt;
        }

        /**
         * 模型调用带重试：只对网关抖动、限流、超时这类可重试异常重试；
         * 参数错误、鉴权失败等不可重试异常立即抛出，避免无效等待与重复计费。
         */
        private String withRetry(String scenario, Supplier<String> call) {
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    String output = call.get();
                    if (attempt > 1) {
                        log.info("ai call succeeded after retry, scenario=ai-call-{}, attempt={}", scenario, attempt);
                    }
                    return output;
                } catch (RuntimeException exception) {
                    lastFailure = exception;
                    boolean retriable = isRetriable(exception);
                    if (!retriable || attempt == MAX_ATTEMPTS) {
                        log.error("ai call failed, scenario=ai-call-{}, attempt={}, retriable={}, errorType={}",
                                scenario, attempt, retriable, exception.getClass().getSimpleName(), exception);
                        throw exception;
                    }
                    log.warn("ai call transient failure, retrying, scenario=ai-call-{}, attempt={}, maxAttempts={}, errorType={}, message={}",
                            scenario, attempt, MAX_ATTEMPTS, exception.getClass().getSimpleName(), exception.getMessage());
                    sleepBeforeRetry(attempt);
                }
            }
            throw lastFailure == null ? new IllegalStateException("ai call failed without exception") : lastFailure;
        }

        private boolean isRetriable(RuntimeException exception) {
            if (exception instanceof UnresolvedModelServerException
                    || exception instanceof RetriableException
                    || exception instanceof TimeoutException) {
                return true;
            }
            if (exception instanceof HttpException httpException) {
                int statusCode = httpException.statusCode();
                return statusCode == 429 || statusCode >= 500;
            }
            return false;
        }

        private void sleepBeforeRetry(int attempt) {
            try {
                Thread.sleep(BASE_BACKOFF_MS * attempt);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("ai call retry interrupted", interrupted);
            }
        }
    }
}

