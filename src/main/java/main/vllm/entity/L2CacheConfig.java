package main.vllm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "l2_cache_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class L2CacheConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private String storageType = "REDIS";

    @Builder.Default
    private String host = "localhost";

    @Builder.Default
    private Integer port = 6379;

    private String password;

    @Builder.Default
    private Integer chunkSize = 256;

    @Builder.Default
    private Double maxLocalCpuSizeGb = 5.0;

    @Builder.Default
    private String remoteSerde = "naive"; // "naive" or "cachegen"

    @Builder.Default
    private String status = "DISABLED"; // "ACTIVE", "DISABLED", "ERROR"

    @Column(columnDefinition = "TEXT")
    private String lastErrorMessage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
