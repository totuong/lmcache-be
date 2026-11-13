package main.vllm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisConnectionTestResponse {
    private Boolean connected;
    private String message;
    private Long latencyMs;
    private String endpoint;
}
