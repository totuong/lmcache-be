package main.vllm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheResponseDTO {
    private Double prefixCacheQueriesTotal;
    private Double prefixCacheHitsTotal;
    private Double prefixCacheHitRatio;
    private Double promptTokensLocalCompute;
    private Double promptTokensLocalCacheHit;
    private Long totalCachedTokensSaved;
    private Double kvCacheUsagePerc;
    private String cacheStatus;
}
