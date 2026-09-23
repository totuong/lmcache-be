package main.vllm.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.vllm.config.LlmProperties;
import main.vllm.dto.ChatRequest;
import main.vllm.dto.ChatResponse;
import main.vllm.dto.UsageInfo;
import main.vllm.exception.LlmException;
import main.vllm.service.LlmService;
import main.vllm.service.VllmMetricsService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaService implements LlmService {

    private final WebClient webClient;
    private final LlmProperties llmProperties;
    private final VllmMetricsService vllmMetricsService;

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        String modelName = request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel()
                : llmProperties.getName();

        log.info("Sending chat request. Target URL: {}/v1/chat/completions, Configured Model Name: {}", llmProperties.getUrl(), modelName);

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(Map.of("role", "user", "content", request.getPrompt())),
                "temperature", 0.7,
                "stream", false
        );

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(responseMap -> {
                    String responseText = extractContentFromResponse(responseMap);
                    UsageInfo usage = extractUsageFromResponse(responseMap);
                    long duration = System.currentTimeMillis() - startTime;

                    return vllmMetricsService.getMetrics()
                            .map(metrics -> ChatResponse.builder()
                                    .response(responseText)
                                    .model(modelName)
                                    .executionTimeMs(duration)
                                    .usage(usage)
                                    .vllmMetrics(metrics)
                                    .build());
                })
                .onErrorMap(throwable -> new LlmException("Failed to generate response from vLLM API: " + throwable.getMessage(), throwable));
    }

    @Override
    public Flux<ChatResponse> chatStream(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        String modelName = request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel()
                : llmProperties.getName();

        log.info("Sending stream request. Target URL: {}/v1/chat/completions, Configured Model Name: {}", llmProperties.getUrl(), modelName);

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(Map.of("role", "user", "content", request.getPrompt())),
                "temperature", 0.7,
                "stream", true
        );

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(responseMap -> {
                    String responseText = extractContentFromStreamChunk(responseMap);
                    UsageInfo usage = extractUsageFromResponse(responseMap);
                    long duration = System.currentTimeMillis() - startTime;
                    return ChatResponse.builder()
                            .response(responseText)
                            .model(modelName)
                            .executionTimeMs(duration)
                            .usage(usage)
                            .build();
                })
                .onErrorMap(throwable -> new LlmException("Failed to stream response from vLLM API: " + throwable.getMessage(), throwable));
    }

    @SuppressWarnings("unchecked")
    private String extractContentFromResponse(Map<String, Object> responseMap) {
        if (responseMap == null) return "";
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            if (message != null && message.containsKey("content")) {
                return (String) message.get("content");
            }
        }
        if (responseMap.containsKey("response")) {
            return (String) responseMap.get("response");
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String extractContentFromStreamChunk(Map<String, Object> responseMap) {
        if (responseMap == null) return "";
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
            if (delta != null && delta.containsKey("content")) {
                return (String) delta.get("content");
            }
        }
        if (responseMap.containsKey("response")) {
            return (String) responseMap.get("response");
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private UsageInfo extractUsageFromResponse(Map<String, Object> responseMap) {
        if (responseMap == null || !responseMap.containsKey("usage")) return null;
        Map<String, Object> usageMap = (Map<String, Object>) responseMap.get("usage");
        if (usageMap == null) return null;

        Integer promptTokens = usageMap.get("prompt_tokens") != null ? ((Number) usageMap.get("prompt_tokens")).intValue() : 0;
        Integer completionTokens = usageMap.get("completion_tokens") != null ? ((Number) usageMap.get("completion_tokens")).intValue() : 0;
        Integer totalTokens = usageMap.get("total_tokens") != null ? ((Number) usageMap.get("total_tokens")).intValue() : 0;

        Integer cachedTokens = 0;
        if (usageMap.containsKey("prompt_tokens_details")) {
            Map<String, Object> details = (Map<String, Object>) usageMap.get("prompt_tokens_details");
            if (details != null && details.containsKey("cached_tokens")) {
                cachedTokens = ((Number) details.get("cached_tokens")).intValue();
            }
        }

        double ratio = (promptTokens > 0) ? ((double) cachedTokens / promptTokens) * 100.0 : 0.0;

        return UsageInfo.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .cachedTokens(cachedTokens)
                .cacheHitRatioPercentage(Math.round(ratio * 100.0) / 100.0)
                .build();
    }
}
