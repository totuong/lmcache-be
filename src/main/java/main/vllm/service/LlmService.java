package main.vllm.service;

import main.vllm.dto.ChatRequest;
import main.vllm.dto.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LlmService {
    Mono<ChatResponse> chat(ChatRequest request);
    Flux<ChatResponse> chatStream(ChatRequest request);
}
