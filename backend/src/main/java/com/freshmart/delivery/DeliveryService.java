package com.freshmart.delivery;

import com.freshmart.auth.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeliveryService {
    private final JdbcTemplate jdbcTemplate;

    public DeliveryService(@Qualifier("deliveryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}
