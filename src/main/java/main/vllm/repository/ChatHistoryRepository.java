package main.vllm.repository;

import main.vllm.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findTop50ByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(c.totalTokens), 0) FROM ChatHistory c")
    Long sumTotalTokens();

    @Query("SELECT COALESCE(SUM(c.cachedTokens), 0) FROM ChatHistory c")
    Long sumCachedTokens();

    @Query("SELECT COALESCE(AVG(c.executionTimeMs), 0.0) FROM ChatHistory c")
    Double averageExecutionTimeMs();
}
