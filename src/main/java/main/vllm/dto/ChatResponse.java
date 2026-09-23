package main.vllm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private String model;
    private Long executionTimeMs;
    private UsageInfo usage;
    private VllmMetricsDTO vllmMetrics;
}
