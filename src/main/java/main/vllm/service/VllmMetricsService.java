package main.vllm.service;

import main.vllm.dto.VllmMetricsDTO;
import reactor.core.publisher.Mono;

public interface VllmMetricsService {
    Mono<VllmMetricsDTO> getMetrics();
}
