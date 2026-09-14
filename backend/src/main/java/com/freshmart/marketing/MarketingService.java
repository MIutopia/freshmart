package com.freshmart.marketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmart.auth.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class MarketingService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MarketingService(@Qualifier("merchantJdbcTemplate") JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional("merchantTransactionManager")
    public long createPromotion(CurrentUser user, String promotionType, String name, Map<String, Object> rule,
            LocalDateTime startsAt, LocalDateTime endsAt, boolean stackable) {
        Long merchantId = jdbcTemplate.query("SELECT id FROM merchants WHERE owner_user_id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), user.userId()).stream().findFirst().orElse(null);
        if (merchantId == null) {
            throw new ResponseStatusException(FORBIDDEN, "active merchant profile is required");
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(BAD_REQUEST, "promotion time range is invalid");
        }
        jdbcTemplate.update("""
                INSERT INTO promotion_rules (merchant_id, promotion_type, name, rule_json, starts_at, ends_at, stackable)
                VALUES (?, ?, ?, CAST(? AS JSON), ?, ?, ?)
                """, merchantId, promotionType, name, json(rule), startsAt, endsAt, stackable);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional("merchantTransactionManager")
    public long createCoupon(CurrentUser user, String name, String couponType, BigDecimal thresholdAmount,
            BigDecimal discountAmount, LocalDateTime startsAt, LocalDateTime endsAt, int totalQuantity) {
        long merchantId = requireActiveMerchant(user);
        if (name == null || name.isBlank() || couponType == null || couponType.isBlank()
                || thresholdAmount == null || thresholdAmount.signum() < 0
                || discountAmount == null || discountAmount.signum() <= 0
                || totalQuantity <= 0 || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(BAD_REQUEST, "coupon fields are invalid");
        }
        if (!"FIXED".equalsIgnoreCase(couponType) && !"FULL_REDUCTION".equalsIgnoreCase(couponType)) {
            throw new ResponseStatusException(BAD_REQUEST, "coupon type must be FIXED or FULL_REDUCTION");
        }
        if (discountAmount.compareTo(thresholdAmount) > 0 && thresholdAmount.signum() > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "discount cannot exceed coupon threshold");
        }
        jdbcTemplate.update("""
                INSERT INTO coupons (merchant_id, name, coupon_type, threshold_amount, discount_amount,
                                     starts_at, ends_at, total_quantity)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, merchantId, name.trim(), couponType.toUpperCase(), thresholdAmount, discountAmount,
                startsAt, endsAt, totalQuantity);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional("merchantTransactionManager")
    public long createFlashSale(CurrentUser user, long productId, BigDecimal salePricePerKg, int totalGrams,
            int perUserLimitGrams, LocalDateTime startsAt, LocalDateTime endsAt) {
        long merchantId = requireActiveMerchant(user);
        if (salePricePerKg == null || salePricePerKg.signum() < 0 || totalGrams <= 0 || perUserLimitGrams <= 0
                || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(BAD_REQUEST, "flash sale fields are invalid");
        }
        boolean ownsProduct = !jdbcTemplate.query("SELECT id FROM products WHERE id = ? AND merchant_id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), productId, merchantId).isEmpty();
        if (!ownsProduct) {
            throw new ResponseStatusException(FORBIDDEN, "active product is not owned by the merchant");
        }
        jdbcTemplate.update("""
                INSERT INTO flash_sale_items (merchant_id, product_id, sale_price_per_kg, total_grams, per_user_limit_grams,
                    starts_at, ends_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, CASE WHEN ? <= CURRENT_TIMESTAMP THEN 'ACTIVE' ELSE 'SCHEDULED' END)
                """, merchantId, productId, salePricePerKg, totalGrams, perUserLimitGrams, startsAt, endsAt, startsAt);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional("merchantTransactionManager")
    public long createMembershipLevel(CurrentUser admin, String name, int minPoints, BigDecimal discountRate) {
        if (name == null || name.isBlank() || minPoints < 0 || discountRate == null
                || discountRate.compareTo(BigDecimal.ZERO) < 0 || discountRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "membership level fields are invalid");
        }
        try {
            jdbcTemplate.update("INSERT INTO membership_levels (name, min_points, discount_rate) VALUES (?, ?, ?)",
                    name.trim(), minPoints, discountRate);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(CONFLICT, "membership level name already exists");
        }
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long requireActiveMerchant(CurrentUser user) {
        Long merchantId = jdbcTemplate.query("SELECT id FROM merchants WHERE owner_user_id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), user.userId()).stream().findFirst().orElse(null);
        if (merchantId == null) {
            throw new ResponseStatusException(FORBIDDEN, "active merchant profile is required");
        }
        return merchantId;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "promotion rule cannot be serialized");
        }
    }
}
