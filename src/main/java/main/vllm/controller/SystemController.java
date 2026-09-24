package main.vllm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import main.vllm.dto.ResponseObject;
import main.vllm.dto.SystemStatusDTO;
import main.vllm.service.ChatHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "System Controller", description = "Endpoints for monitoring system health, LLM model settings, and database connectivity")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping({"/api/v1/system", "/system"})
@RequiredArgsConstructor
public class SystemController {

    private final ChatHistoryService chatHistoryService;

    @Operation(summary = "Get system status", description = "Returns overall health, database status, target LLM model information, and system runtime stats.")
    @GetMapping
    public Mono<ResponseEntity<ResponseObject<SystemStatusDTO>>> getSystemStatus() {
        return chatHistoryService.getSystemStatus()
                .map(status -> ResponseEntity.ok(ResponseObject.success(status, "System status retrieved successfully")));
    }
}
