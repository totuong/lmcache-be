package main.vllm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import main.vllm.dto.ResponseObject;
import main.vllm.dto.VllmMetricsDTO;
import main.vllm.service.VllmMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Metrics Controller", description = "Endpoints for checking vLLM engine metrics, prefix cache hits, and performance statistics")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping({"/api/v1/metrics", "/metrics"})
@RequiredArgsConstructor
public class MetricsController {

    private final VllmMetricsService vllmMetricsService;

    @Operation(summary = "Get vLLM engine metrics", description = "Fetches and parses real-time Prometheus metrics from vLLM engine, including prefix cache hit stats, KV cache usage, and request latencies.")
    @GetMapping
    public Mono<ResponseEntity<ResponseObject<VllmMetricsDTO>>> getMetrics() {
        return vllmMetricsService.getMetrics()
                .map(metrics -> ResponseEntity.ok(ResponseObject.success(metrics, "Metrics fetched successfully")));
    }
}
