package main.vllm.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.vllm.config.LlmProperties;
import main.vllm.dto.CacheResponseDTO;
import main.vllm.dto.ChatResponse;
import main.vllm.dto.SystemStatusDTO;
import main.vllm.dto.UsageInfo;
import main.vllm.entity.ChatHistory;
import main.vllm.repository.ChatHistoryRepository;
import main.vllm.service.ChatHistoryService;
import main.vllm.service.VllmMetricsService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.scheduling.annotation.Async;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final LlmProperties llmProperties;
    private final VllmMetricsService vllmMetricsService;

    private static final long START_TIME = System.currentTimeMillis();

    @Override
    public Mono<ChatHistory> saveChat(String prompt, ChatResponse response) {
        return Mono.fromCallable(() -> buildAndSaveEntity(prompt, response))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    @Async
    public void saveChatAsync(String prompt, ChatResponse response) {
        try {
            buildAndSaveEntity(prompt, response);
        } catch (Exception e) {
            log.error("Failed to asynchronously save chat history to PostgreSQL DB: {}", e.getMessage());
        }
    }

    private ChatHistory buildAndSaveEntity(String prompt, ChatResponse response) {
        UsageInfo usage = response != null ? response.getUsage() : null;

        ChatHistory chatHistory = ChatHistory.builder()
                .prompt(prompt)
                .response(response != null ? response.getResponse() : null)
                .model(response != null && response.getModel() != null ? response.getModel() : llmProperties.getName())
                .executionTimeMs(response != null ? response.getExecutionTimeMs() : null)
                .promptTokens(usage != null ? usage.getPromptTokens() : null)
                .completionTokens(usage != null ? usage.getCompletionTokens() : null)
                .totalTokens(usage != null ? usage.getTotalTokens() : null)
                .cachedTokens(usage != null ? usage.getCachedTokens() : null)
                .cacheHitRatioPercentage(usage != null ? usage.getCacheHitRatioPercentage() : null)
                .createdAt(LocalDateTime.now())
                .build();

        ChatHistory saved = chatHistoryRepository.save(chatHistory);
        log.info("Async saved chat history record to PostgreSQL database with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public Mono<List<ChatHistory>> getRecentHistory(int limit) {
        return Mono.fromCallable(chatHistoryRepository::findTop50ByOrderByCreatedAtDesc)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> getTotalChatsCount() {
        return Mono.fromCallable(chatHistoryRepository::count)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<CacheResponseDTO> getCacheStats() {
        return vllmMetricsService.getMetrics()
                .flatMap(vllmMetrics -> Mono.fromCallable(() -> {
                    Long totalCachedTokensSaved = chatHistoryRepository.sumCachedTokens();

                    return CacheResponseDTO.builder()
                            .prefixCacheQueriesTotal(vllmMetrics.getPrefixCacheQueriesTotal())
                            .prefixCacheHitsTotal(vllmMetrics.getPrefixCacheHitsTotal())
                            .prefixCacheHitRatio(vllmMetrics.getPrefixCacheHitRatio())
                            .promptTokensLocalCompute(vllmMetrics.getPromptTokensLocalCompute())
                            .promptTokensLocalCacheHit(vllmMetrics.getPromptTokensLocalCacheHit())
                            .totalCachedTokensSaved(totalCachedTokensSaved)
                            .kvCacheUsagePerc(vllmMetrics.getKvCacheUsagePerc())
                            .cacheStatus("ACTIVE")
                            .build();
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @Override
    public Mono<SystemStatusDTO> getSystemStatus() {
        return Mono.fromCallable(() -> {
            long totalChats = chatHistoryRepository.count();
            String dbStatus = "CONNECTED";
            long uptimeSeconds = (System.currentTimeMillis() - START_TIME) / 1000;

            Runtime runtime = Runtime.getRuntime();
            long freeMemoryMb = runtime.freeMemory() / (1024 * 1024);
            long totalMemoryMb = runtime.totalMemory() / (1024 * 1024);

            return SystemStatusDTO.builder()
                    .applicationName("vLLM Cache Backend")
                    .version("1.0.0")
                    .status("UP")
                    .llmModelName(llmProperties.getName())
                    .llmModelUrl(llmProperties.getUrl())
                    .databaseStatus(dbStatus)
                    .totalChatsProcessed(totalChats)
                    .uptimeSeconds(uptimeSeconds)
                    .freeMemoryMb(freeMemoryMb)
                    .totalMemoryMb(totalMemoryMb)
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
