package main.vllm.service;

import main.vllm.dto.CacheResponseDTO;
import main.vllm.dto.ChatResponse;
import main.vllm.dto.SystemStatusDTO;
import main.vllm.entity.ChatHistory;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ChatHistoryService {

    Mono<ChatHistory> saveChat(String prompt, ChatResponse response);

    void saveChatAsync(String prompt, ChatResponse response);

    Mono<List<ChatHistory>> getRecentHistory(int limit);

    Mono<Long> getTotalChatsCount();

    Mono<CacheResponseDTO> getCacheStats();

    Mono<SystemStatusDTO> getSystemStatus();
}
