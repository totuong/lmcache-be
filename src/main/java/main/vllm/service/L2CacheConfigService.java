package main.vllm.service;

import main.vllm.dto.L2CacheConfigDTO;
import main.vllm.dto.RedisConnectionTestRequest;
import main.vllm.dto.RedisConnectionTestResponse;
import reactor.core.publisher.Mono;

public interface L2CacheConfigService {

    Mono<RedisConnectionTestResponse> testConnection(RedisConnectionTestRequest request);

    Mono<L2CacheConfigDTO> getCurrentConfig();

    Mono<L2CacheConfigDTO> saveAndApplyConfig(L2CacheConfigDTO request);

    Mono<L2CacheConfigDTO> disableL2Cache();

    Mono<String> getYamlPreview();
}
