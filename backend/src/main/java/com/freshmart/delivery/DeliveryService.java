package com.freshmart.delivery;

import com.freshmart.auth.CurrentUser;
import com.freshmart.platform.PlatformRuleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class DeliveryService {
    private final JdbcTemplate jdbcTemplate;
    private final PlatformRuleService platformRuleService;
    private final int defaultAcceptTimeoutMinutes;

    public DeliveryService(@Qualifier("deliveryJdbcTemplate") JdbcTemplate jdbcTemplate,
            PlatformRuleService platformRuleService,
            @Value("${commerce.delivery.accept-timeout-minutes:5}") int defaultAcceptTimeoutMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformRuleService = platformRuleService;
        this.defaultAcceptTimeoutMinutes = defaultAcceptTimeoutMinutes;
    }

    @Transactional("deliveryTransactionManager")
    public long createZone(String name, String areaCode, String boundaryJson) {
        jdbcTemplate.update("INSERT INTO delivery_zones (name, area_code, boundary_json) VALUES (?, ?, ?)", name, areaCode, boundaryJson);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional("deliveryTransactionManager")
    public void updateZone(long zoneId, String name, String boundaryJson, String status) {
        int updated = jdbcTemplate.update("UPDATE delivery_zones SET name = ?, boundary_json = ?, status = ? WHERE id = ?",
                name, boundaryJson, status, zoneId);
        if (updated == 0) {
            throw new ResponseStatusException(NOT_FOUND, "delivery zone not found");
        }
    }

    public List<DeliveryZoneView> listZones() {
        return jdbcTemplate.query("SELECT id, name, area_code, boundary_json, status FROM delivery_zones ORDER BY id", (rs, row) ->
                new DeliveryZoneView(rs.getLong("id"), rs.getString("name"), rs.getString("area_code"),
                        rs.getString("boundary_json"), rs.getString("status")));
    }

    public List<DeliveryTaskView> listMyTasks(CurrentUser rider) {
        return jdbcTemplate.query("""
                SELECT id, order_id, merchant_id, warehouse_id, delivery_zone_id, status, accept_deadline_at,
                       assigned_at, accepted_at, picked_at, delivered_at, proof_url, exception_note
                FROM delivery_tasks WHERE rider_user_id = ? ORDER BY assigned_at DESC, id DESC
                """, (rs, row) -> new DeliveryTaskView(
                rs.getLong("id"), rs.getLong("order_id"), rs.getLong("merchant_id"), rs.getLong("warehouse_id"),
                rs.getLong("delivery_zone_id"), rs.getString("status"), rs.getObject("accept_deadline_at", LocalDateTime.class),
                rs.getObject("assigned_at", LocalDateTime.class), rs.getObject("accepted_at", LocalDateTime.class),
                rs.getObject("picked_at", LocalDateTime.class), rs.getObject("delivered_at", LocalDateTime.class),
                rs.getString("proof_url"), rs.getString("exception_note")), rider.userId());
    }

    @Transactional("deliveryTransactionManager")
    public void assign(long taskId, long riderUserId) {
        int acceptTimeoutMinutes = platformRuleService.integerOrDefault("delivery.accept.timeout.minutes",
                defaultAcceptTimeoutMinutes);
        if (acceptTimeoutMinutes <= 0) {
            throw new ResponseStatusException(CONFLICT, "delivery accept timeout must be positive");
        }
        List<Long> zones = jdbcTemplate.query("""
                SELECT task.delivery_zone_id FROM delivery_tasks task
                JOIN rider_profiles rider ON rider.user_id = ? AND rider.status = 'ACTIVE'
                WHERE task.id = ? AND (rider.current_zone_id IS NULL OR rider.current_zone_id = task.delivery_zone_id)
                """, (rs, row) -> rs.getLong(1), riderUserId, taskId);
        if (zones.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "eligible rider or waiting task not found");
        }
        int updated = jdbcTemplate.update("""
                UPDATE delivery_tasks SET rider_user_id = ?, status = 'ASSIGNED', assigned_at = CURRENT_TIMESTAMP,
                    accept_deadline_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? MINUTE)
                WHERE id = ? AND status = 'WAITING_ASSIGNMENT' AND rider_user_id IS NULL
                """, riderUserId, acceptTimeoutMinutes, taskId);
        if (updated == 0) {
            throw new ResponseStatusException(CONFLICT, "delivery task is no longer available for assignment");
        }
        updatePerformance(riderUserId, "assigned_count", 1);
    }

    @Transactional("deliveryTransactionManager")
    public void accept(CurrentUser rider, long taskId) {
        int updated = jdbcTemplate.update("""
                UPDATE delivery_tasks SET status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP
                WHERE id = ? AND rider_user_id = ? AND status = 'ASSIGNED' AND accept_deadline_at >= CURRENT_TIMESTAMP
                """, taskId, rider.userId());
        if (updated == 0) {
            throw new ResponseStatusException(CONFLICT, "delivery task cannot be accepted");
        }
        updatePerformance(rider.userId(), "accepted_count", 1);
    }

    @Transactional("deliveryTransactionManager")
    public void pick(CurrentUser rider, long taskId) {
        updateRiderTask(taskId, rider.userId(), "ACCEPTED", "PICKED", "picked_at", null);
    }

    @Transactional("deliveryTransactionManager")
    public void deliver(CurrentUser rider, long taskId, String proofUrl) {
        updateRiderTask(taskId, rider.userId(), "PICKED", "DELIVERED", "delivered_at", proofUrl);
        updatePerformance(rider.userId(), "delivered_count", 1);
        jdbcTemplate.update("""
                UPDATE rider_performance_daily
                SET on_time_rate = CASE WHEN assigned_count = 0 THEN 0 ELSE ROUND(delivered_count * 100 / assigned_count, 2) END
                WHERE rider_user_id = ? AND stat_date = CURRENT_DATE
                """, rider.userId());
    }

    @Transactional("deliveryTransactionManager")
    public void releaseTimedOutAssignments() {
        List<Long> timedOutRiders = jdbcTemplate.query("""
                SELECT rider_user_id FROM delivery_tasks
                WHERE status = 'ASSIGNED' AND accept_deadline_at < CURRENT_TIMESTAMP AND rider_user_id IS NOT NULL
                FOR UPDATE
                """, (rs, row) -> rs.getLong(1));
        for (Long riderUserId : timedOutRiders) {
            updatePerformance(riderUserId, "timeout_count", 1);
        }
        jdbcTemplate.update("""
                UPDATE delivery_tasks SET rider_user_id = NULL, status = 'WAITING_ASSIGNMENT', timeout_at = CURRENT_TIMESTAMP,
                    accept_deadline_at = NULL
                WHERE status = 'ASSIGNED' AND accept_deadline_at < CURRENT_TIMESTAMP
                """);
    }

    public List<RiderPerformanceView> listMyPerformance(CurrentUser rider, LocalDate from, LocalDate to) {
        return jdbcTemplate.query("""
                SELECT stat_date, assigned_count, accepted_count, delivered_count, timeout_count, on_time_rate
                FROM rider_performance_daily
                WHERE rider_user_id = ? AND stat_date >= ? AND stat_date <= ?
                ORDER BY stat_date DESC
                """, (rs, row) -> new RiderPerformanceView(
                rs.getObject("stat_date", LocalDate.class), rs.getInt("assigned_count"), rs.getInt("accepted_count"),
                rs.getInt("delivered_count"), rs.getInt("timeout_count"), rs.getBigDecimal("on_time_rate")), rider.userId(), from, to);
    }

    public record DeliveryZoneView(long id, String name, String areaCode, String boundaryJson, String status) {
    }

    public record DeliveryTaskView(long id, long orderId, long merchantId, long warehouseId, long deliveryZoneId,
            String status, LocalDateTime acceptDeadlineAt, LocalDateTime assignedAt, LocalDateTime acceptedAt,
            LocalDateTime pickedAt, LocalDateTime deliveredAt, String proofUrl, String exceptionNote) {
    }

    public record RiderPerformanceView(LocalDate statDate, int assignedCount, int acceptedCount, int deliveredCount,
            int timeoutCount, BigDecimal onTimeRate) {
    }

    private void updateRiderTask(long taskId, long riderUserId, String sourceStatus, String targetStatus, String timestampColumn, String proofUrl) {
        String sql = proofUrl == null
                ? "UPDATE delivery_tasks SET status = ?, " + timestampColumn + " = CURRENT_TIMESTAMP WHERE id = ? AND rider_user_id = ? AND status = ?"
                : "UPDATE delivery_tasks SET status = ?, " + timestampColumn + " = CURRENT_TIMESTAMP, proof_url = ? WHERE id = ? AND rider_user_id = ? AND status = ?";
        int updated = proofUrl == null
                ? jdbcTemplate.update(sql, targetStatus, taskId, riderUserId, sourceStatus)
                : jdbcTemplate.update(sql, targetStatus, proofUrl, taskId, riderUserId, sourceStatus);
        if (updated == 0) {
            throw new ResponseStatusException(CONFLICT, "delivery task is not in the expected state");
        }
        if ("PICKED".equals(targetStatus) || "DELIVERED".equals(targetStatus)) {
            jdbcTemplate.update("UPDATE freshmart_trade.orders SET status = ? WHERE id = ?", targetStatus, taskIdToOrderId(taskId));
        }
    }

    private long taskIdToOrderId(long taskId) {
        return jdbcTemplate.query("SELECT order_id FROM delivery_tasks WHERE id = ?", (rs, row) -> rs.getLong(1), taskId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "delivery task not found"));
    }

    private void updatePerformance(long riderUserId, String metric, int increment) {
        if (!List.of("assigned_count", "accepted_count", "delivered_count", "timeout_count").contains(metric)) {
            throw new IllegalArgumentException("unsupported rider performance metric");
        }
        jdbcTemplate.update("""
                INSERT INTO rider_performance_daily (rider_user_id, stat_date, assigned_count, accepted_count, delivered_count, timeout_count)
                VALUES (?, CURRENT_DATE, 0, 0, 0, 0)
                ON DUPLICATE KEY UPDATE rider_user_id = VALUES(rider_user_id)
                """, riderUserId);
        jdbcTemplate.update("UPDATE rider_performance_daily SET " + metric + " = " + metric + " + ? WHERE rider_user_id = ? AND stat_date = CURRENT_DATE",
                increment, riderUserId);
    }
}
