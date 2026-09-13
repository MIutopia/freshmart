package com.freshmart.trade;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryReservationExpiryJob {
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate userJdbcTemplate;

    public InventoryReservationExpiryJob(@Qualifier("tradeJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${commerce.inventory-release-interval-ms:60000}")
    @Transactional("tradeTransactionManager")
    public void releaseExpiredReservations() {
        List<Reservation> expired = jdbcTemplate.query("""
                SELECT reservation.id, reservation.trade_id, reservation.product_id, reservation.batch_id, reservation.reserved_grams
                FROM inventory_reservations reservation
                JOIN trade_orders trade ON trade.id = reservation.trade_id
                WHERE reservation.status = 'ACTIVE' AND reservation.expires_at <= CURRENT_TIMESTAMP
                  AND trade.status = 'PENDING_PAYMENT'
                FOR UPDATE
                """, (rs, row) -> new Reservation(rs.getLong("id"), rs.getLong("trade_id"), rs.getLong("product_id"),
                rs.getLong("batch_id"), rs.getInt("reserved_grams")));
        for (Reservation reservation : expired) {
            jdbcTemplate.update("""
                    UPDATE freshmart_merchant.inventory_batches
                    SET reserved_grams = reserved_grams - ?
                    WHERE id = ? AND product_id = ? AND reserved_grams >= ?
                    """, reservation.grams(), reservation.batchId(), reservation.productId(), reservation.grams());
            jdbcTemplate.update("""
                    UPDATE inventory_reservations SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'ACTIVE'
                    """, reservation.id());
        }
        if (!expired.isEmpty()) {
            List<Long> tradeIds = expired.stream().map(Reservation::tradeId).distinct().toList();
            for (Long tradeId : tradeIds) {
                jdbcTemplate.update("UPDATE trade_orders SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING_PAYMENT'", tradeId);
                jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE trade_id = ? AND status = 'PENDING_PAYMENT'", tradeId);
                jdbcTemplate.update("UPDATE payment_orders SET status = 'EXPIRED' WHERE trade_id = ? AND status = 'PENDING'", tradeId);
                userJdbcTemplate.update("""
                        UPDATE user_coupons SET status = 'AVAILABLE', used_trade_id = NULL
                        WHERE used_trade_id = ? AND status = 'RESERVED'
                        """, tradeId);
            }
        }
        List<FlashReservation> expiredFlash = jdbcTemplate.query("""
                SELECT id, flash_sale_id, reserved_grams FROM flash_sale_reservations
                WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP FOR UPDATE
                """, (rs, row) -> new FlashReservation(rs.getLong("id"), rs.getLong("flash_sale_id"), rs.getInt("reserved_grams")));
        for (FlashReservation reservation : expiredFlash) {
            jdbcTemplate.update("""
                    UPDATE freshmart_merchant.flash_sale_items SET reserved_grams = reserved_grams - ?
                    WHERE id = ? AND reserved_grams >= ?
                    """, reservation.grams(), reservation.flashSaleId(), reservation.grams());
            jdbcTemplate.update("""
                    UPDATE flash_sale_reservations SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'ACTIVE'
                    """, reservation.id());
        }
    }

    private record Reservation(long id, long tradeId, long productId, long batchId, int grams) {
    }

    private record FlashReservation(long id, long flashSaleId, int grams) {
    }
}
