package main.vllm.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.vllm.config.LmcacheProperties;
import main.vllm.dto.L2CacheConfigDTO;
import main.vllm.dto.RedisConnectionTestRequest;
import main.vllm.dto.RedisConnectionTestResponse;
import main.vllm.entity.L2CacheConfig;
import main.vllm.repository.L2CacheConfigRepository;
import main.vllm.service.L2CacheConfigService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class L2CacheConfigServiceImpl implements L2CacheConfigService {

    private final L2CacheConfigRepository configRepository;
    private final LmcacheProperties lmcacheProperties;

    @PostConstruct
    public void init() {
        try {
            Path path = Path.of(lmcacheProperties.getConfigFilePath());
            if (!Files.exists(path)) {
                log.info("LMCache config file does not exist at {}. Generating default template...", path.toAbsolutePath());
                Optional<L2CacheConfig> latestOpt = configRepository.findFirstByOrderByIdDesc();
                L2CacheConfig config = latestOpt.orElseGet(this::createDefaultEntity);
                writeYamlFile(config);
            }
        } catch (Exception e) {
            log.warn("Could not auto-initialize lmcache YAML file: {}", e.getMessage());
        }
    }

    @Override
    public Mono<RedisConnectionTestResponse> testConnection(RedisConnectionTestRequest request) {
        return Mono.fromCallable(() -> doTestRedisConnection(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<L2CacheConfigDTO> getCurrentConfig() {
        return Mono.fromCallable(() -> {
            L2CacheConfig entity = configRepository.findFirstByOrderByIdDesc()
                    .orElseGet(this::createDefaultEntity);
            return mapToDTO(entity);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<L2CacheConfigDTO> saveAndApplyConfig(L2CacheConfigDTO request) {
        return Mono.fromCallable(() -> {
            L2CacheConfig currentEntity = configRepository.findFirstByOrderByIdDesc()
                    .orElseGet(this::createDefaultEntity);

            boolean isEnabled = Boolean.TRUE.equals(request.getEnabled());
            String host = (request.getHost() != null && !request.getHost().isBlank()) ? request.getHost().trim() : "localhost";
            int port = (request.getPort() != null && request.getPort() > 0) ? request.getPort() : 6379;
            
            // Password resolution: preserve existing password if masked or blank
            String effectivePassword = currentEntity.getPassword();
            if (request.getPassword() != null && !request.getPassword().isBlank() && !request.getPassword().equals("******")) {
                effectivePassword = request.getPassword().trim();
            }

            int chunkSize = (request.getChunkSize() != null && request.getChunkSize() > 0)
                    ? request.getChunkSize()
                    : lmcacheProperties.getDefaultChunkSize();

            double cpuSize = (request.getMaxLocalCpuSizeGb() != null && request.getMaxLocalCpuSizeGb() > 0)
                    ? request.getMaxLocalCpuSizeGb()
                    : lmcacheProperties.getDefaultMaxLocalCpuSizeGb();

            String serde = (request.getRemoteSerde() != null && !request.getRemoteSerde().isBlank())
                    ? request.getRemoteSerde().trim().toLowerCase()
                    : lmcacheProperties.getDefaultRemoteSerde();

            String status = "DISABLED";
            String lastError = null;

            if (isEnabled) {
                // Validate connectivity to Redis before applying
                RedisConnectionTestResponse testResult = doTestRedisConnection(RedisConnectionTestRequest.builder()
                        .host(host)
                        .port(port)
                        .password(effectivePassword)
                        .timeoutMs(3000)
                        .build());

                if (!Boolean.TRUE.equals(testResult.getConnected())) {
                    lastError = testResult.getMessage();
                    status = "ERROR";
                    log.error("Failed to connect to Redis during config apply: {}", lastError);
                    throw new IllegalArgumentException("Không thể kết nối đến Redis server: " + lastError);
                } else {
                    status = "ACTIVE";
                }
            }

            currentEntity.setEnabled(isEnabled);
            currentEntity.setStorageType("REDIS");
            currentEntity.setHost(host);
            currentEntity.setPort(port);
            currentEntity.setPassword(effectivePassword);
            currentEntity.setChunkSize(chunkSize);
            currentEntity.setMaxLocalCpuSizeGb(cpuSize);
            currentEntity.setRemoteSerde(serde);
            currentEntity.setStatus(status);
            currentEntity.setLastErrorMessage(lastError);

            L2CacheConfig saved = configRepository.save(currentEntity);

            // Write YAML configuration
            writeYamlFile(saved);

            // Trigger restart if configured
            if (Boolean.TRUE.equals(lmcacheProperties.getAutoRestartVllm())) {
                restartVllmService();
            }

            return mapToDTO(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<L2CacheConfigDTO> disableL2Cache() {
        return Mono.fromCallable(() -> {
            L2CacheConfig currentEntity = configRepository.findFirstByOrderByIdDesc()
                    .orElseGet(this::createDefaultEntity);

            currentEntity.setEnabled(false);
            currentEntity.setStatus("DISABLED");
            currentEntity.setLastErrorMessage(null);

            L2CacheConfig saved = configRepository.save(currentEntity);
            writeYamlFile(saved);

            if (Boolean.TRUE.equals(lmcacheProperties.getAutoRestartVllm())) {
                restartVllmService();
            }

            return mapToDTO(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> getYamlPreview() {
        return Mono.fromCallable(() -> {
            L2CacheConfig entity = configRepository.findFirstByOrderByIdDesc()
                    .orElseGet(this::createDefaultEntity);
            return renderYaml(entity);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== Private Helper Methods ====================

    private RedisConnectionTestResponse doTestRedisConnection(RedisConnectionTestRequest request) {
        String host = (request.getHost() != null && !request.getHost().isBlank()) ? request.getHost().trim() : "localhost";
        int port = (request.getPort() != null && request.getPort() > 0) ? request.getPort() : 6379;
        int timeout = (request.getTimeoutMs() != null && request.getTimeoutMs() > 0) ? request.getTimeoutMs() : 3000;
        String password = request.getPassword();

        long startTime = System.currentTimeMillis();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);

            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // Authenticate if password provided
            if (password != null && !password.isBlank() && !password.equals("******")) {
                out.write(("AUTH " + password + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();

                String authReply = reader.readLine();
                if (authReply == null) {
                    return RedisConnectionTestResponse.builder()
                            .connected(false)
                            .message("Redis closed connection during AUTH")
                            .endpoint(host + ":" + port)
                            .latencyMs(System.currentTimeMillis() - startTime)
                            .build();
                }

                if (!authReply.startsWith("+OK")) {
                    return RedisConnectionTestResponse.builder()
                            .connected(false)
                            .message("Redis AUTH error: " + authReply)
                            .endpoint(host + ":" + port)
                            .latencyMs(System.currentTimeMillis() - startTime)
                            .build();
                }
            }

            // Send PING
            out.write("PING\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String pingReply = reader.readLine();
            long latency = System.currentTimeMillis() - startTime;

            if (pingReply != null && (pingReply.startsWith("+PONG") || pingReply.contains("PONG"))) {
                return RedisConnectionTestResponse.builder()
                        .connected(true)
                        .message("Kết nối Redis thành công! PING -> " + pingReply)
                        .endpoint(host + ":" + port)
                        .latencyMs(latency)
                        .build();
            } else {
                return RedisConnectionTestResponse.builder()
                        .connected(false)
                        .message("Redis PING phản hồi không hợp lệ: " + pingReply)
                        .endpoint(host + ":" + port)
                        .latencyMs(latency)
                        .build();
            }

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Redis connection test failed to {}:{}: {}", host, port, e.getMessage());
            return RedisConnectionTestResponse.builder()
                    .connected(false)
                    .message("Không thể kết nối đến Redis: " + e.getMessage())
                    .endpoint(host + ":" + port)
                    .latencyMs(latency)
                    .build();
        }
    }

    private void writeYamlFile(L2CacheConfig config) throws Exception {
        String yaml = renderYaml(config);
        Path targetPath = Path.of(lmcacheProperties.getConfigFilePath());
        
        File parentDir = targetPath.toFile().getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        Files.writeString(
                targetPath,
                yaml,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
        log.info("Successfully updated LMCache YAML config at: {}", targetPath.toAbsolutePath());
    }

    private String renderYaml(L2CacheConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ================================================================\n");
        sb.append("# LMCache Configuration File (Auto-generated by Spring Boot)\n");
        sb.append("# ================================================================\n\n");

        sb.append("# Chunk size (number of tokens per KV chunk)\n");
        sb.append("chunk_size: ").append(config.getChunkSize() != null ? config.getChunkSize() : 256).append("\n\n");

        sb.append("# Tier 1: Local CPU Cache\n");
        sb.append("local_cpu: true\n");
        sb.append("max_local_cpu_size: ").append(config.getMaxLocalCpuSizeGb() != null ? config.getMaxLocalCpuSizeGb() : 5.0).append("\n\n");

        if (Boolean.TRUE.equals(config.getEnabled()) && config.getHost() != null && !config.getHost().isBlank()) {
            String remoteUrl = buildRemoteUrl(config);
            String serde = config.getRemoteSerde() != null ? config.getRemoteSerde() : "naive";

            sb.append("# Tier 2: Remote Distributed Storage (Redis)\n");
            sb.append("remote_url: \"").append(remoteUrl).append("\"\n");
            sb.append("remote_serde: \"").append(serde).append("\"\n");
        } else {
            sb.append("# Tier 2: Remote Distributed Storage (Disabled)\n");
            sb.append("# remote_url: \"redis://localhost:6379\"\n");
            sb.append("# remote_serde: \"naive\"\n");
        }

        return sb.toString();
    }

    private String buildRemoteUrl(L2CacheConfig config) {
        String host = config.getHost() != null ? config.getHost() : "localhost";
        int port = config.getPort() != null ? config.getPort() : 6379;

        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            return String.format("redis://:%s@%s:%d", config.getPassword(), host, port);
        } else {
            return String.format("redis://%s:%d", host, port);
        }
    }

    private void restartVllmService() {
        String restartCmd = lmcacheProperties.getRestartCommand();

        // 1. Nếu có cấu hình lệnh shell trực tiếp cho Ubuntu (Systemd / Script bash)
        if (restartCmd != null && !restartCmd.isBlank()) {
            log.info("Executing native Ubuntu/Linux restart command: '{}'...", restartCmd);
            try {
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                ProcessBuilder pb = isWindows
                        ? new ProcessBuilder("cmd.exe", "/c", restartCmd)
                        : new ProcessBuilder("bash", "-c", restartCmd);

                pb.redirectErrorStream(true);
                Process process = pb.start();

                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int code = process.waitFor();
                log.info("Native restart command '{}' finished with exit code {}. Output:\n{}", restartCmd, code, output);
            } catch (Exception e) {
                log.error("Failed to execute native restart command '{}': {}", restartCmd, e.getMessage());
            }
            return;
        }

        // 2. Fallback sang Docker nếu chạy trong môi trường container
        String container = lmcacheProperties.getVllmContainerName();
        log.info("No native restart command configured. Attempting docker restart for '{}'...", container);
        try {
            Process process = new ProcessBuilder("docker", "restart", container)
                    .redirectErrorStream(true)
                    .start();
            int code = process.waitFor();
            log.info("Docker restart container '{}' finished with exit code {}", container, code);
        } catch (Exception e) {
            log.warn("Notice: Could not restart container '{}': {}. (If running native Ubuntu, configure 'lmcache.restart-command').",
                    container, e.getMessage());
        }
    }

    private L2CacheConfig createDefaultEntity() {
        return L2CacheConfig.builder()
                .enabled(false)
                .storageType("REDIS")
                .host("localhost")
                .port(6379)
                .chunkSize(lmcacheProperties.getDefaultChunkSize())
                .maxLocalCpuSizeGb(lmcacheProperties.getDefaultMaxLocalCpuSizeGb())
                .remoteSerde(lmcacheProperties.getDefaultRemoteSerde())
                .status("DISABLED")
                .build();
    }

    private L2CacheConfigDTO mapToDTO(L2CacheConfig entity) {
        boolean hasPass = entity.getPassword() != null && !entity.getPassword().isBlank();
        String maskedPass = hasPass ? "******" : null;

        String remoteUrl = null;
        if (Boolean.TRUE.equals(entity.getEnabled()) && entity.getHost() != null) {
            remoteUrl = buildRemoteUrl(entity);
            // Mask password in URL for display
            if (hasPass) {
                remoteUrl = String.format("redis://:******@%s:%d", entity.getHost(), entity.getPort());
            }
        }

        return L2CacheConfigDTO.builder()
                .id(entity.getId())
                .enabled(entity.getEnabled())
                .storageType(entity.getStorageType())
                .host(entity.getHost())
                .port(entity.getPort())
                .hasPassword(hasPass)
                .maskedPassword(maskedPass)
                .chunkSize(entity.getChunkSize())
                .maxLocalCpuSizeGb(entity.getMaxLocalCpuSizeGb())
                .remoteSerde(entity.getRemoteSerde())
                .status(entity.getStatus())
                .lastErrorMessage(entity.getLastErrorMessage())
                .remoteUrl(remoteUrl)
                .yamlContent(renderYaml(entity))
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
