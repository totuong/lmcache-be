package main.vllm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VllmMetricsDTO {
    private Double prefixCacheQueriesTotal;
    private Double prefixCacheHitsTotal;
    private Double prefixCacheHitRatio; // (hits / queries) * 100
    private Double promptTokensLocalCompute;
    private Double promptTokensLocalCacheHit;
    private Double kvCacheUsagePerc;
    private Double numRequestsRunning;
    private Double numRequestsWaiting;
    private Double promptTokensTotal;
    private Double generationTokensTotal;
    private Double e2eLatencyAvgSeconds;
    private Double timeToFirstTokenAvgSeconds;
}
