package main.vllm.repository;

import main.vllm.entity.L2CacheConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface L2CacheConfigRepository extends JpaRepository<L2CacheConfig, Long> {

    Optional<L2CacheConfig> findFirstByOrderByIdDesc();
}
