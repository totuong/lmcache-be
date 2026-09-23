package main.vllm.service.impl;

import main.vllm.dto.ChatResponse;
import main.vllm.service.MessageQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogMessageQueueService implements MessageQueueService {

    @Override
    public void publishChatMessage(String topic, ChatResponse message) {
        log.info("[Event Publisher] Simulated publishing chat message to Kafka topic '{}': {}", topic, message);
    }
}
