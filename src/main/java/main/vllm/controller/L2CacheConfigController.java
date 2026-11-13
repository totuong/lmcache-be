package main.vllm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.vllm.dto.L2CacheConfigDTO;
import main.vllm.dto.RedisConnectionTestRequest;
import main.vllm.dto.RedisConnectionTestResponse;
import main.vllm.dto.ResponseObject;
import main.vllm.service.L2CacheConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "L2 Cache Controller", description = "Endpoints for configuring, testing, and toggling LMCache L2 Remote Cache (Redis/Distributed KV Cache)")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping({"/api/v1/cache/l2", "/cache/l2"})
@RequiredArgsConstructor
@Slf4j
public class L2CacheConfigController {

    private final L2CacheConfigService l2CacheConfigService;

    @Operation(summary = "Get current L2 cache configuration", description = "Returns active L2 cache settings, storage backend (Redis), remote URL, and status.")
    @GetMapping
    public Mono<ResponseEntity<ResponseObject<L2CacheConfigDTO>>> getCurrentConfig() {
        return l2CacheConfigService.getCurrentConfig()
                .map(config -> ResponseEntity.ok(ResponseObject.success(config, "L2 cache configuration retrieved successfully")));
    }

    @Operation(summary = "Test Redis connection", description = "Pings the specified Redis host and port to verify network reachability and authentication before applying.")
    @PostMapping("/test")
    public Mono<ResponseEntity<ResponseObject<RedisConnectionTestResponse>>> testRedisConnection(@RequestBody RedisConnectionTestRequest request) {
        return l2CacheConfigService.testConnection(request)
                .map(result -> {
                    if (Boolean.TRUE.equals(result.getConnected())) {
                        return ResponseEntity.ok(ResponseObject.success(result, "Kết nối Redis thành công!"));
                    } else {
                        return ResponseEntity.badRequest().body(ResponseObject.<RedisConnectionTestResponse>builder()
                                .status("ERROR")
                                .message(result.getMessage())
                                .data(result)
                                .build());
                    }
                });
    }

    @Operation(summary = "Save and apply L2 cache configuration", description = "Validates connection, saves settings to database, rewrites lmcache_vllm.yaml, and reloads vLLM engine.")
    @PostMapping
    public Mono<ResponseEntity<ResponseObject<L2CacheConfigDTO>>> saveAndApplyConfig(@RequestBody L2CacheConfigDTO request) {
        return l2CacheConfigService.saveAndApplyConfig(request)
                .map(saved -> ResponseEntity.ok(ResponseObject.success(saved, "Cấu hình L2 Cache đã được lưu và áp dụng thành công!")))
                .onErrorResume(IllegalArgumentException.class, e -> {
                    log.error("Validation error saving L2 cache config: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(ResponseObject.error(e.getMessage())));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected error saving L2 cache config: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError().body(ResponseObject.error("Lỗi khi áp dụng cấu hình: " + e.getMessage())));
                });
    }

    @Operation(summary = "Disable L2 cache", description = "Disables remote L2 tier, reverts LMCache to local L1 CPU/GPU only, and rewrites YAML.")
    @DeleteMapping
    public Mono<ResponseEntity<ResponseObject<L2CacheConfigDTO>>> disableL2Cache() {
        return l2CacheConfigService.disableL2Cache()
                .map(saved -> ResponseEntity.ok(ResponseObject.success(saved, "Đã tắt L2 Remote Cache thành công. Hệ thống chuyển về L1 CPU Cache.")));
    }

    @Operation(summary = "Preview LMCache YAML file", description = "Returns the raw content of the generated lmcache_vllm.yaml file.")
    @GetMapping("/yaml")
    public Mono<ResponseEntity<ResponseObject<String>>> getYamlPreview() {
        return l2CacheConfigService.getYamlPreview()
                .map(yaml -> ResponseEntity.ok(ResponseObject.success(yaml, "LMCache YAML preview fetched successfully")));
    }
}
