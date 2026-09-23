package main.vllm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        log.info("Endpoint documentation available at: /swagger-ui.html or /v3/api-docs");
        return new OpenAPI()
                .info(new Info()
                        .title("vLLM LLM Cache & Chat API")
                        .version("1.0.0")
                        .description("SOLID Spring Boot Chat LLM Integration with Ollama/vLLM services, supporting standard and WebFlux reactive streaming responses.")
                        .contact(new Contact()
                                .name("Antigravity AI Dev Team")
                                .email("dev@vllm-cache.io")));
    }
}
