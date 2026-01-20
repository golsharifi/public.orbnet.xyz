package com.orbvpn.api.service.webhook;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookEventLogger {
    private final JdbcTemplate jdbcTemplate;

    public void logEvent(String eventType, String payload) {
        String sql = "INSERT INTO webhook_event_logs (event_type, payload) VALUES (?, ?)";
        jdbcTemplate.update(sql, eventType, payload);
    }

    public void updateEventStatus(Long deliveryId, boolean success) {
        String sql = success ? "UPDATE webhook_event_logs SET success_count = success_count + 1 WHERE id = ?"
                : "UPDATE webhook_event_logs SET failure_count = failure_count + 1 WHERE id = ?";
        jdbcTemplate.update(sql, deliveryId);
    }

    public Map<String, Integer> getEventStats(String eventType, LocalDateTime since) {
        String sql = "SELECT " +
                "COUNT(*) as total, " +
                "COALESCE(SUM(success_count), 0) as successes, " +
                "COALESCE(SUM(failure_count), 0) as failures " +
                "FROM webhook_event_logs " +
                "WHERE event_type = ? AND created_at >= ?";

        Map<String, Object> result = jdbcTemplate.queryForMap(sql, eventType, since);

        // Convert to Map<String, Integer>
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", ((Number) result.get("total")).intValue());
        stats.put("successes", ((Number) result.get("successes")).intValue());
        stats.put("failures", ((Number) result.get("failures")).intValue());

        return stats;
    }
}