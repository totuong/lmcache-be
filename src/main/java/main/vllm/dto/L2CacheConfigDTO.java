package main.vllm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2CacheConfigDTO {
    private Long id;
    private Boolean enabled;
    private String storageType;
    private String host;
    private Integer port;
    private String password;
    private Boolean hasPassword;
    private String maskedPassword;
    private Integer chunkSize;
    private Double maxLocalCpuSizeGb;
    private String remoteSerde;
    private String status;
    private String lastErrorMessage;
    private String remoteUrl;
    private String yamlContent;
    private LocalDateTime updatedAt;
}
