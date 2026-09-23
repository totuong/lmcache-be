package main.vllm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private final LlmProperties llmProperties;

    public WebClientConfig(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(llmProperties.getUrl())
                .build();
    }
}
