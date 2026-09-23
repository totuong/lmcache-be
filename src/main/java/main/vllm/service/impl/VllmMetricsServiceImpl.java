package main.vllm.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.vllm.config.LlmProperties;
import main.vllm.dto.VllmMetricsDTO;
import main.vllm.service.VllmMetricsService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Scanner;

@Service
@RequiredArgsConstructor
@Slf4j
public class VllmMetricsServiceImpl implements VllmMetricsService {

    private final WebClient webClient;
    private final LlmProperties llmProperties;

    @Override
    public Mono<VllmMetricsDTO> getMetrics() {
        log.info("Fetching raw metrics from vLLM engine at {}/metrics", llmProperties.getUrl());

        return webClient.get()
                .uri("/metrics")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parsePrometheusMetrics)
                .onErrorResume(throwable -> {
                    log.error("Failed to fetch metrics from vLLM: {}", throwable.getMessage());
                    return Mono.just(new VllmMetricsDTO());
                });
    }

    private VllmMetricsDTO parsePrometheusMetrics(String rawMetrics) {
        VllmMetricsDTO.VllmMetricsDTOBuilder builder = VllmMetricsDTO.builder();

        double prefixCacheQueries = 0.0;
        double prefixCacheHits = 0.0;
        double promptLocalCompute = 0.0;
        double promptLocalCacheHit = 0.0;
        double kvCacheUsage = 0.0;
        double numRunning = 0.0;
        double numWaiting = 0.0;
        double promptTokensTotal = 0.0;
        double generationTokensTotal = 0.0;

        double e2eSum = 0.0;
        double e2eCount = 0.0;
        double ttftSum = 0.0;
        double ttftCount = 0.0;

        if (rawMetrics == null || rawMetrics.isBlank()) {
            return builder.build();
        }

        try (Scanner scanner = new Scanner(rawMetrics)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Split line into metric_with_labels and value
                int lastSpaceIndex = line.lastIndexOf(' ');
                if (lastSpaceIndex == -1) {
                    continue;
                }

                String metricWithLabels = line.substring(0, lastSpaceIndex).trim();
                String valueStr = line.substring(lastSpaceIndex + 1).trim();

                double val;
                try {
                    val = Double.parseDouble(valueStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                if (metricWithLabels.startsWith("vllm:prefix_cache_queries_total")) {
                    prefixCacheQueries = val;
                } else if (metricWithLabels.startsWith("vllm:prefix_cache_hits_total")) {
                    prefixCacheHits = val;
                } else if (metricWithLabels.startsWith("vllm:prompt_tokens_by_source_total")) {
                    if (metricWithLabels.contains("source=\"local_compute\"")) {
                        promptLocalCompute = val;
                    } else if (metricWithLabels.contains("source=\"local_cache_hit\"")) {
                        promptLocalCacheHit = val;
                    }
                } else if (metricWithLabels.startsWith("vllm:kv_cache_usage_perc")) {
                    kvCacheUsage = val;
                } else if (metricWithLabels.startsWith("vllm:num_requests_running")) {
                    numRunning = val;
                } else if (metricWithLabels.startsWith("vllm:num_requests_waiting") && !metricWithLabels.contains("reason")) {
                    numWaiting = val;
                } else if (metricWithLabels.startsWith("vllm:prompt_tokens_total")) {
                    promptTokensTotal = val;
                } else if (metricWithLabels.startsWith("vllm:generation_tokens_total")) {
                    generationTokensTotal = val;
                } else if (metricWithLabels.startsWith("vllm:e2e_request_latency_seconds_sum")) {
                    e2eSum = val;
                } else if (metricWithLabels.startsWith("vllm:e2e_request_latency_seconds_count")) {
                    e2eCount = val;
                } else if (metricWithLabels.startsWith("vllm:time_to_first_token_seconds_sum")) {
                    ttftSum = val;
                } else if (metricWithLabels.startsWith("vllm:time_to_first_token_seconds_count")) {
                    ttftCount = val;
                }
            }
        }

        double hitRatio = (prefixCacheQueries > 0) ? (prefixCacheHits / prefixCacheQueries) * 100.0 : 0.0;
        double e2eAvg = (e2eCount > 0) ? (e2eSum / e2eCount) : 0.0;
        double ttftAvg = (ttftCount > 0) ? (ttftSum / ttftCount) : 0.0;

        return builder
                .prefixCacheQueriesTotal(prefixCacheQueries)
                .prefixCacheHitsTotal(prefixCacheHits)
                .prefixCacheHitRatio(Math.round(hitRatio * 100.0) / 100.0)
                .promptTokensLocalCompute(promptLocalCompute)
                .promptTokensLocalCacheHit(promptLocalCacheHit)
                .kvCacheUsagePerc(kvCacheUsage)
                .numRequestsRunning(numRunning)
                .numRequestsWaiting(numWaiting)
                .promptTokensTotal(promptTokensTotal)
                .generationTokensTotal(generationTokensTotal)
                .e2eLatencyAvgSeconds(Math.round(e2eAvg * 1000.0) / 1000.0)
                .timeToFirstTokenAvgSeconds(Math.round(ttftAvg * 1000.0) / 1000.0)
                .build();
    }
}
