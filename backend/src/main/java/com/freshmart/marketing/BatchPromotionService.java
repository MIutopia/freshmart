package com.freshmart.marketing;

import com.freshmart.auth.AuditLogService;
import com.freshmart.auth.CurrentUser;
import com.freshmart.platform.PlatformRuleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BatchPromotionService {
    private final JdbcTemplate merchantJdbcTemplate;
    private final PlatformRuleService platformRuleService;
    private final AuditLogService auditLogService;

    public BatchPromotionService(@Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            PlatformRuleService platformRuleService, AuditLogService auditLogService) {
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.platformRuleService = platformRuleService;
        this.auditLogService = auditLogService;
    }

    public List<CandidateView> candidates() {
        int thresholdDays = Math.max(0, platformRuleService.integerOrDefault("batch.near.expiry.days", 3));
        return merchantJdbcTemplate.query("""
                SELECT batch.id, batch.product_id, product.name product_name, batch.warehouse_id,
                       batch.batch_no, batch.available_grams, batch.reserved_grams, batch.expires_on,
                       product.merchant_id
                FROM inventory_batches batch
                JOIN products product ON product.id = batch.product_id
                WHERE batch.expires_on IS NOT NULL
                  AND batch.expires_on >= CURRENT_DATE
                  AND batch.expires_on <= DATE_ADD(CURRENT_DATE, INTERVAL ? DAY)
                  AND batch.available_grams > batch.reserved_grams
                  AND product.status = 'ACTIVE'
                ORDER BY batch.expires_on, batch.id
                """, (rs, row) -> new CandidateView(rs.getLong("id"), rs.getLong("product_id"),
                rs.getString("product_name"), rs.getLong("merchant_id"), rs.getLong("warehouse_id"),
                rs.getString("batch_no"), rs.getInt("available_grams"), rs.getInt("reserved_grams"),
                rs.getObject("expires_on", LocalDate.class)), thresholdDays);
    }

    @Transactional("merchantTransactionManager")
    public PromotionView confirm(CurrentUser admin, long batchId, BigDecimal markdownRate,
            LocalDateTime startsAt, LocalDateTime endsAt, String sourceIp) {
        if (markdownRate == null || markdownRate.compareTo(BigDecimal.ZERO) <= 0
                || markdownRate.compareTo(BigDecimal.valueOf(100)) >= 0
                || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(BAD_REQUEST, "batch promotion fields are invalid");
        }
        Batch batch = merchantJdbcTemplate.query("""
                SELECT id, expires_on FROM inventory_batches
                WHERE id = ? AND available_grams > reserved_grams
                """, (rs, row) -> new Batch(rs.getLong("id"), rs.getObject("expires_on", LocalDate.class)), batchId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "available batch not found"));
        if (batch.expiresOn() == null || endsAt.toLocalDate().isAfter(batch.expiresOn())) {
            throw new ResponseStatusException(BAD_REQUEST, "promotion must end before batch expiry");
        }
        Integer overlap = merchantJdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM batch_promotions
                WHERE batch_id = ? AND status IN ('SCHEDULED', 'ACTIVE')
                  AND starts_at < ? AND ends_at > ?
                """, Integer.class, batchId, endsAt, startsAt);
        if (overlap != null && overlap > 0) {
            throw new ResponseStatusException(CONFLICT, "batch promotion time overlaps an existing promotion");
        }
        merchantJdbcTemplate.update("""
                INSERT INTO batch_promotions (batch_id, markdown_rate, starts_at, ends_at, status)
                VALUES (?, ?, ?, ?, 'SCHEDULED')
                """, batchId, markdownRate, startsAt, endsAt);
        long id = merchantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditLogService.record(admin.userId(), "BATCH_PROMOTION_CONFIRMED", "BATCH_PROMOTION", Long.toString(id), sourceIp);
        return new PromotionView(id, batchId, markdownRate, startsAt, endsAt, "SCHEDULED");
    }

    private record Batch(long id, LocalDate expiresOn) {
    }

    public record CandidateView(long batchId, long productId, String productName, long merchantId,
            long warehouseId, String batchNo, int availableGrams, int reservedGrams, LocalDate expiresOn) {
    }

    public record PromotionView(long id, long batchId, BigDecimal markdownRate,
            LocalDateTime startsAt, LocalDateTime endsAt, String status) {
    }
}
