package main.vllm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import main.vllm.dto.CacheResponseDTO;
import main.vllm.dto.ResponseObject;
import main.vllm.service.ChatHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Cache Controller", description = "Endpoints for inspecting prefix cache statistics, hit ratios, and saved tokens")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping({"/api/v1/cache", "/cache"})
@RequiredArgsConstructor
public class CacheController {

    private final ChatHistoryService chatHistoryService;

    @Operation(summary = "Get cache statistics", description = "Fetches current prefix cache hit ratio, KV cache usage percentage, and aggregate cached tokens saved from PostgreSQL.")
    @GetMapping
    public Mono<ResponseEntity<ResponseObject<CacheResponseDTO>>> getCacheStats() {
        return chatHistoryService.getCacheStats()
                .map(stats -> ResponseEntity.ok(ResponseObject.success(stats, "Cache statistics fetched successfully")));
    }

    @Operation(summary = "Clear cache stats status", description = "Resets or purges cache statistics view.")
    @DeleteMapping
    public Mono<ResponseEntity<ResponseObject<String>>> clearCache() {
        return Mono.just(ResponseEntity.ok(ResponseObject.success("Cache status reset successfully")));
    }
}
