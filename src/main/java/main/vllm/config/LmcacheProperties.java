package main.vllm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "lmcache")
public class LmcacheProperties {

    /**
     * Path to lmcache_vllm.yaml configuration file
     */
    private String configFilePath = "lmcache_vllm.yaml";

    /**
     * Name of the Docker container running vLLM with LMCache
     */
    private String vllmContainerName = "vllm-engine";

    /**
     * Whether to automatically invoke docker restart when config changes
     */
    private Boolean autoRestartVllm = false;

    /**
     * Default chunk size (tokens per chunk)
     */
    private Integer defaultChunkSize = 256;

    /**
     * Default local CPU cache size in GB
     */
    private Double defaultMaxLocalCpuSizeGb = 5.0;

    /**
     * Default serialization format: naive or cachegen
     */
    private String defaultRemoteSerde = "naive";

    /**
     * Command to restart native vLLM process on Ubuntu/Linux
     * Example: "sudo systemctl restart vllm" or "/home/user/restart_vllm.sh"
     */
    private String restartCommand;
}
