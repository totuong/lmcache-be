package main.vllm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusDTO {
    private String applicationName;
    private String version;
    private String status;
    private String llmModelName;
    private String llmModelUrl;
    private String databaseStatus;
    private Long totalChatsProcessed;
    private Long uptimeSeconds;
    private Long freeMemoryMb;
    private Long totalMemoryMb;
}
