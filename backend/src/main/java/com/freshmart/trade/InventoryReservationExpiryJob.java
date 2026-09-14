package com.freshmart.trade;

import java.util.List;
import com.freshmart.payment.FinancialStatusLogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class InventoryReservationExpiryJob {
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate userJdbcTemplate;
    private final FinancialStatusLogService statusLogService;
    private final TransactionTemplate userTransactionTemplate;

    public InventoryReservationExpiryJob(@Qualifier("tradeJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate,
            @Qualifier("userTransactionManager") PlatformTransactionManager userTransactionManager,
            FinancialStatusLogService statusLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
        this.statusLogService = statusLogService;
        this.userTransactionTemplate = new TransactionTemplate(userTransactionManager);
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
                List<PaymentState> payments = jdbcTemplate.query("SELECT payment_no, status FROM payment_orders WHERE trade_id = ? AND status IN ('PENDING', 'PROOF_SUBMITTED') FOR UPDATE",
                        (rs, row) -> new PaymentState(rs.getString(1), rs.getString(2)), tradeId);
                jdbcTemplate.update("UPDATE payment_orders SET status = 'CANCELLED', failure_code = 'PAYMENT_TIMEOUT', failure_message = '库存预占超时，支付单已取消' WHERE trade_id = ? AND status IN ('PENDING', 'PROOF_SUBMITTED')", tradeId);
                payments.forEach(payment -> statusLogService.record("PAYMENT", payment.paymentNo(), payment.status(), "CANCELLED", "PAYMENT_TIMEOUT_CANCELLED", null, "库存预占超时", "SYSTEM"));
                userJdbcTemplate.update("""
                        UPDATE user_coupons SET status = 'AVAILABLE', used_trade_id = NULL
                        WHERE used_trade_id = ? AND status = 'RESERVED'
                        """, tradeId);
            }
        }
        releaseCancelledTradeRedemptions();
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

    private void releaseCancelledTradeRedemptions() {
        List<CancelledTrade> cancelledTrades = jdbcTemplate.query("""
                SELECT id, user_id, redeemed_points
                FROM trade_orders
                WHERE status = 'CANCELLED' AND user_id IS NOT NULL AND redeemed_points > 0
                """, (rs, row) -> new CancelledTrade(rs.getLong("id"), rs.getLong("user_id"),
                rs.getInt("redeemed_points")));
        for (CancelledTrade trade : cancelledTrades) {
            userTransactionTemplate.executeWithoutResult(status -> releasePoints(trade));
        }
    }

    private void releasePoints(CancelledTrade trade) {
        String idempotencyKey = "POINTS-RELEASE-" + trade.tradeId();
        userJdbcTemplate.update("INSERT IGNORE INTO user_point_accounts (user_id) VALUES (?)", trade.userId());
        int inserted = userJdbcTemplate.update("""
                INSERT IGNORE INTO points_transactions (user_id, trade_id, change_amount, balance_after, reason, idempotency_key)
                VALUES (?, ?, ?, 0, 'REDEMPTION_RELEASED', ?)
                """, trade.userId(), trade.tradeId(), trade.redeemedPoints(), idempotencyKey);
        if (inserted == 0) {
            return;
        }
        userJdbcTemplate.update("UPDATE user_point_accounts SET available_points = available_points + ? WHERE user_id = ?",
                trade.redeemedPoints(), trade.userId());
        Integer balance = userJdbcTemplate.queryForObject("SELECT available_points FROM user_point_accounts WHERE user_id = ?",
                Integer.class, trade.userId());
        userJdbcTemplate.update("UPDATE points_transactions SET balance_after = ? WHERE idempotency_key = ?", balance,
                idempotencyKey);
    }

    private record Reservation(long id, long tradeId, long productId, long batchId, int grams) {
    }

    private record PaymentState(String paymentNo, String status) {
    }

    private record CancelledTrade(long tradeId, long userId, int redeemedPoints) {
    }

    private record FlashReservation(long id, long flashSaleId, int grams) {
    }
}
