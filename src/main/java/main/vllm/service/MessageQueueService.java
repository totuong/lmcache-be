package main.vllm.service;

import main.vllm.dto.ChatResponse;

public interface MessageQueueService {
    void publishChatMessage(String topic, ChatResponse message);
}
