package com.freshmart.marketing;

import com.freshmart.auth.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MemberBenefitService {
    private final JdbcTemplate jdbcTemplate;

    public MemberBenefitService(@Qualifier("userJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CouponView> listClaimableCoupons(CurrentUser user) {
        return jdbcTemplate.query("""
                SELECT coupon.id, coupon.name, coupon.coupon_type, coupon.threshold_amount, coupon.discount_amount,
                       coupon.ends_at, coupon.total_quantity, coupon.claimed_quantity
                FROM freshmart_merchant.coupons coupon
                LEFT JOIN user_coupons user_coupon ON user_coupon.coupon_id = coupon.id AND user_coupon.user_id = ?
                WHERE coupon.status = 'ACTIVE' AND coupon.starts_at <= CURRENT_TIMESTAMP AND coupon.ends_at > CURRENT_TIMESTAMP
                  AND coupon.claimed_quantity < coupon.total_quantity AND user_coupon.id IS NULL
                ORDER BY coupon.ends_at ASC, coupon.id DESC
                """, (rs, row) -> new CouponView(rs.getLong("id"), rs.getString("name"), rs.getString("coupon_type"),
                rs.getBigDecimal("threshold_amount"), rs.getBigDecimal("discount_amount"),
                rs.getObject("ends_at", LocalDateTime.class), rs.getInt("total_quantity"), rs.getInt("claimed_quantity")), user.userId());
    }

    @Transactional("userTransactionManager")
    public void claimCoupon(CurrentUser user, long couponId) {
        int created = jdbcTemplate.update("""
                INSERT IGNORE INTO user_coupons (user_id, coupon_id, status)
                SELECT ?, id, 'AVAILABLE' FROM freshmart_merchant.coupons
                WHERE id = ? AND status = 'ACTIVE' AND starts_at <= CURRENT_TIMESTAMP AND ends_at > CURRENT_TIMESTAMP
                """, user.userId(), couponId);
        if (created == 0) {
            throw new ResponseStatusException(CONFLICT, "coupon is unavailable or has already been claimed");
        }
        int reserved = jdbcTemplate.update("""
                UPDATE freshmart_merchant.coupons SET claimed_quantity = claimed_quantity + 1
                WHERE id = ? AND status = 'ACTIVE' AND claimed_quantity < total_quantity AND ends_at > CURRENT_TIMESTAMP
                """, couponId);
        if (reserved == 0) {
            throw new ResponseStatusException(CONFLICT, "coupon has been fully claimed");
        }
    }

    public MembershipView membership(CurrentUser user) {
        Integer points = jdbcTemplate.query("""
                SELECT balance_after FROM points_transactions WHERE user_id = ? ORDER BY id DESC LIMIT 1
                """, (rs, row) -> rs.getInt(1), user.userId()).stream().findFirst().orElse(0);
        return jdbcTemplate.query("""
                SELECT id, name, min_points, discount_rate FROM freshmart_merchant.membership_levels
                WHERE status = 'ACTIVE' AND min_points <= ? ORDER BY min_points DESC LIMIT 1
                """, (rs, row) -> new MembershipView(points, rs.getLong("id"), rs.getString("name"),
                rs.getInt("min_points"), rs.getBigDecimal("discount_rate")), points).stream().findFirst()
                .orElse(new MembershipView(points, null, "普通会员", 0, BigDecimal.valueOf(100)));
    }

    public record CouponView(long id, String name, String couponType, BigDecimal thresholdAmount,
            BigDecimal discountAmount, LocalDateTime endsAt, int totalQuantity, int claimedQuantity) {
    }

    public record MembershipView(int points, Long levelId, String levelName, int minPoints, BigDecimal discountRate) {
    }
}
