package main.vllm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.vllm.dto.ChatRequest;
import main.vllm.dto.ChatResponse;
import main.vllm.dto.ResponseObject;
import main.vllm.entity.ChatHistory;
import main.vllm.service.ChatHistoryService;
import main.vllm.service.LlmService;
import main.vllm.service.MessageQueueService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Tag(name = "Chat Controller", description = "Endpoints for prompt-based chat, streaming responses, and chat history retrieval")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping({"/api/v1/chat", "/chat"})
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final LlmService llmService;
    private final MessageQueueService messageQueueService;
    private final ChatHistoryService chatHistoryService;

    @Operation(summary = "Get full chat response", description = "Executes the LLM request, returns the complete text response, and persists the QA pair into PostgreSQL database.")
    @PostMapping
    public Mono<ResponseEntity<ResponseObject<ChatResponse>>> chat(@RequestBody ChatRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ResponseObject.error("Prompt cannot be null or empty")
            ));
        }

        return llmService.chat(request)
                .map(chatResponse -> {
                    // Publish message to queue
                    messageQueueService.publishChatMessage("chat-responses", chatResponse);

                    // Save chat entry asynchronously in background without blocking response
                    chatHistoryService.saveChatAsync(request.getPrompt(), chatResponse);

                    return ResponseEntity.ok(ResponseObject.success(chatResponse, "Response generated successfully"));
                });
    }

    @Operation(summary = "Stream chat response chunks", description = "Streams the response text chunk-by-chunk in real-time as Server-Sent Events (SSE) and persists the complete response upon stream end.")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ResponseObject<ChatResponse>> chatStream(@RequestBody ChatRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return Flux.just(ResponseObject.error("Prompt cannot be null or empty"));
        }

        StringBuilder fullResponseBuilder = new StringBuilder();
        AtomicReference<ChatResponse> lastChunkRef = new AtomicReference<>();

        return llmService.chatStream(request)
                .map(chatResponse -> {
                    if (chatResponse.getResponse() != null) {
                        fullResponseBuilder.append(chatResponse.getResponse());
                    }
                    lastChunkRef.set(chatResponse);

                    // Publish stream chunk to message queue
                    messageQueueService.publishChatMessage("chat-responses-stream", chatResponse);

                    return ResponseObject.success(chatResponse);
                })
                .doOnComplete(() -> {
                    ChatResponse lastChunk = lastChunkRef.get();
                    ChatResponse accumulatedResponse = ChatResponse.builder()
                            .response(fullResponseBuilder.toString())
                            .model(lastChunk != null ? lastChunk.getModel() : null)
                            .executionTimeMs(lastChunk != null ? lastChunk.getExecutionTimeMs() : null)
                            .usage(lastChunk != null ? lastChunk.getUsage() : null)
                            .build();

                    // Non-blocking async background save
                    chatHistoryService.saveChatAsync(request.getPrompt(), accumulatedResponse);
                });
    }

    @Operation(summary = "Get stored chat history", description = "Retrieves recent chat prompts and answers stored in the PostgreSQL database.")
    @GetMapping("/history")
    public Mono<ResponseEntity<ResponseObject<List<ChatHistory>>>> getHistory(
            @RequestParam(defaultValue = "50") int limit) {
        return chatHistoryService.getRecentHistory(limit)
                .map(history -> ResponseEntity.ok(ResponseObject.success(history, "Chat history fetched successfully")));
    }
}
