package main.vllm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisConnectionTestRequest {
    private String host;
    private Integer port;
    private String password;
    private Integer timeoutMs;
}
