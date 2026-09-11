package com.freshmart.trade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmart.auth.CurrentUser;
import com.freshmart.order.FreightCalculator;
import com.freshmart.order.MarketPriceSettlementPolicy;
import com.freshmart.marketing.PromotionCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TradeService {
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate userJdbcTemplate;
    private final JdbcTemplate merchantJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int reservationMinutes;
    private final BigDecimal freeFreightThreshold;
    private final BigDecimal standardFreight;
    private final BigDecimal maxMarkupRate;
    private final BigDecimal pointsPerCurrency;

    public TradeService(
            @Qualifier("tradeJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${commerce.inventory-reservation-minutes:15}") int reservationMinutes,
            @Value("${commerce.freight.free-threshold:59.00}") BigDecimal freeFreightThreshold,
            @Value("${commerce.freight.standard-fee:6.00}") BigDecimal standardFreight,
            @Value("${commerce.market-price.max-markup-rate:5.00}") BigDecimal maxMarkupRate,
            @Value("${commerce.points-per-currency:1.00}") BigDecimal pointsPerCurrency) {
        this.jdbcTemplate = jdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.objectMapper = objectMapper;
        this.reservationMinutes = reservationMinutes;
        this.freeFreightThreshold = freeFreightThreshold;
        this.standardFreight = standardFreight;
        this.maxMarkupRate = maxMarkupRate;
        this.pointsPerCurrency = pointsPerCurrency;
    }

    @Transactional("tradeTransactionManager")
    public TradeView create(CurrentUser user, long deliveryZoneId, Map<String, Object> addressSnapshot,
            List<CheckoutLine> requestedLines, String idempotencyKey) {
        return create(user, deliveryZoneId, addressSnapshot, requestedLines, List.of(), idempotencyKey);
    }

    @Transactional("tradeTransactionManager")
    public TradeView create(CurrentUser user, long deliveryZoneId, Map<String, Object> addressSnapshot,
            List<CheckoutLine> requestedLines, List<Long> couponIds, String idempotencyKey) {
        TradeView existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (existing.userId() != user.userId()) {
                throw new ResponseStatusException(FORBIDDEN, "idempotency key belongs to another user");
            }
            return existing;
        }
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "at least one checkout line is required");
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(reservationMinutes);
        Map<Long, List<PricedLine>> merchantLines = new LinkedHashMap<>();
        Map<Long, Long> merchantWarehouseIds = new LinkedHashMap<>();
        for (CheckoutLine requestedLine : requestedLines) {
            if (requestedLine.weightGrams() <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "requested weight must be positive");
            }
            ProductWarehouse product = findProductWarehouse(requestedLine.productId(), deliveryZoneId);
            Long selectedWarehouse = merchantWarehouseIds.putIfAbsent(product.merchantId(), product.warehouseId());
            if (selectedWarehouse != null && selectedWarehouse.longValue() != product.warehouseId()) {
                throw new ResponseStatusException(CONFLICT, "one merchant suborder cannot span multiple warehouses");
            }
            MarketPriceSettlementPolicy.PriceBreakdown price;
            try {
                price = MarketPriceSettlementPolicy.calculate(requestedLine.weightGrams(), product.marketPricePerKg(),
                        product.merchantPricePerKg(), maxMarkupRate);
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
            }
            merchantLines.computeIfAbsent(product.merchantId(), ignored -> new ArrayList<>())
                    .add(new PricedLine(product, requestedLine.weightGrams(), price));
        }

        Map<Long, List<PromotionCalculator.Promotion>> merchantPromotions = loadPromotions(user.userId(), couponIds, merchantLines.keySet());
        int points = userJdbcTemplate.query("SELECT balance_after FROM points_transactions WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                (rs, row) -> rs.getInt(1), user.userId()).stream().findFirst().orElse(0);
        Map<Long, PromotionCalculator.DiscountResult> merchantDiscounts = new HashMap<>();
        BigDecimal goodsAmount = BigDecimal.ZERO;
        BigDecimal discountAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, List<PricedLine>> entry : merchantLines.entrySet()) {
            BigDecimal merchantGoods = entry.getValue().stream().map(line -> line.price().userGoodsAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal membershipRate = merchantJdbcTemplate.query("""
                    SELECT discount_rate FROM membership_levels WHERE status = 'ACTIVE' AND min_points <= ?
                    ORDER BY min_points DESC LIMIT 1
                    """, (rs, row) -> rs.getBigDecimal(1), points).stream().findFirst().orElse(BigDecimal.valueOf(100));
            PromotionCalculator.DiscountResult discount = PromotionCalculator.calculate(merchantGoods, membershipRate,
                    merchantPromotions.getOrDefault(entry.getKey(), List.of()));
            boolean containsIneligibleCoupon = merchantPromotions.getOrDefault(entry.getKey(), List.of()).stream()
                    .anyMatch(promotion -> merchantGoods.compareTo(promotion.thresholdAmount()) < 0);
            if (containsIneligibleCoupon) {
                throw new ResponseStatusException(CONFLICT, "coupon threshold is not met");
            }
            merchantDiscounts.put(entry.getKey(), discount);
            goodsAmount = goodsAmount.add(merchantGoods);
            discountAmount = discountAmount.add(discount.discountAmount());
        }
        BigDecimal freightAmount = BigDecimal.ZERO;
        for (Long merchantId : merchantLines.keySet()) {
            freightAmount = freightAmount.add(FreightCalculator.calculate(merchantDiscounts.get(merchantId).payableGoodsAmount(),
                    freeFreightThreshold, standardFreight));
        }
        String tradeNo = newNo("T");
        jdbcTemplate.update("""
                INSERT INTO trade_orders (trade_no, user_id, goods_amount, freight_amount, discount_amount, payable_amount,
                                          reservation_expires_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, tradeNo, user.userId(), goodsAmount, freightAmount, discountAmount,
                goodsAmount.subtract(discountAmount).add(freightAmount), expiresAt, idempotencyKey);
        long tradeId = lastInsertId();
        String addressJson = json(addressSnapshot);
        List<String> orderNos = new ArrayList<>();
        for (Map.Entry<Long, List<PricedLine>> entry : merchantLines.entrySet()) {
            long merchantId = entry.getKey();
            List<PricedLine> lines = entry.getValue();
            long warehouseId = merchantWarehouseIds.get(merchantId);
            BigDecimal merchantGoods = lines.stream().map(line -> line.price().userGoodsAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            PromotionCalculator.DiscountResult discount = merchantDiscounts.get(merchantId);
            BigDecimal merchantFreight = FreightCalculator.calculate(discount.payableGoodsAmount(), freeFreightThreshold, standardFreight);
            String orderNo = newNo("O");
            jdbcTemplate.update("""
                    INSERT INTO orders (order_no, trade_id, user_id, merchant_id, warehouse_id, delivery_zone_id, goods_amount,
                                        freight_amount, discount_amount, payable_amount, address_snapshot, pricing_snapshot)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON))
                    """, orderNo, tradeId, user.userId(), merchantId, warehouseId, deliveryZoneId, merchantGoods,
                    merchantFreight, discount.discountAmount(), discount.payableGoodsAmount().add(merchantFreight), addressJson,
                    json(Map.of("priceSource", "MARKET_CAP", "promotionDiscount", discount.discountAmount())));
            long orderId = lastInsertId();
            orderNos.add(orderNo);
            for (PricedLine line : lines) {
                jdbcTemplate.update("""
                        INSERT INTO order_items (order_id, product_id, product_name_snapshot, warehouse_id, weight_grams,
                                                 market_price_per_kg, merchant_price_per_kg, user_price_per_kg,
                                                 merchant_gross_amount, user_goods_amount, platform_price_subsidy_amount)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, orderId, line.product().productId(), line.product().name(), warehouseId, line.weightGrams(),
                        line.product().marketPricePerKg(), line.product().merchantPricePerKg(), line.price().userPricePerKilogram(),
                        line.price().merchantGrossAmount(), line.price().userGoodsAmount(), line.price().platformSubsidyAmount());
                reserveBatches(tradeId, orderId, line.product().productId(), warehouseId, line.weightGrams(), expiresAt);
            }
        }
        if (!reserveCoupons(user.userId(), tradeId, couponIds)) {
            throw new ResponseStatusException(CONFLICT, "selected coupon is no longer available");
        }
        return new TradeView(tradeId, tradeNo, user.userId(), "PENDING_PAYMENT", goodsAmount, freightAmount, discountAmount,
                goodsAmount.subtract(discountAmount).add(freightAmount), expiresAt, List.copyOf(orderNos));
    }

    @Transactional("tradeTransactionManager")
    public PaymentView prepay(CurrentUser user, String tradeNo) {
        TradeView trade = requireOwnedPendingTrade(user, tradeNo);
        List<PaymentView> existing = jdbcTemplate.query("""
                SELECT payment_no, status, amount, code_url FROM payment_orders
                WHERE trade_id = ? AND status = 'PENDING' ORDER BY id DESC LIMIT 1
                """, (rs, row) -> new PaymentView(rs.getString("payment_no"), rs.getString("status"),
                rs.getBigDecimal("amount"), rs.getString("code_url")), trade.id());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String paymentNo = newNo("P");
        String codeUrl = "SIMULATED://freshmart/pay/" + paymentNo;
        jdbcTemplate.update("""
                INSERT INTO payment_orders (payment_no, trade_id, provider, payment_mode, code_url, amount, idempotency_key)
                VALUES (?, ?, 'SIMULATED', 'NATIVE', ?, ?, ?)
                """, paymentNo, trade.id(), codeUrl, trade.payableAmount(), "PREPAY-" + trade.tradeNo());
        return new PaymentView(paymentNo, "PENDING", trade.payableAmount(), codeUrl);
    }

    @Transactional("tradeTransactionManager")
    public TradeView confirmSimulatedPayment(CurrentUser user, String tradeNo) {
        return settlePaidTrade(user, tradeNo);
    }

    @Transactional("tradeTransactionManager")
    public TradeView confirmBalancePayment(CurrentUser user, String tradeNo) {
        TradeView trade = requireOwnedPendingTrade(user, tradeNo);
        if (trade.reservationExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(CONFLICT, "inventory reservation has expired");
        }
        int deducted = userJdbcTemplate.update("""
                UPDATE wallet_accounts SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND status = 'ACTIVE' AND balance >= ?
                """, trade.payableAmount(), user.userId(), trade.payableAmount());
        if (deducted == 0) {
            throw new ResponseStatusException(CONFLICT, "insufficient wallet balance");
        }
        try {
            BigDecimal balance = userJdbcTemplate.query("SELECT balance FROM wallet_accounts WHERE user_id = ?",
                    (rs, row) -> rs.getBigDecimal(1), user.userId()).stream().findFirst().orElse(BigDecimal.ZERO);
            userJdbcTemplate.update("""
                    INSERT INTO wallet_transactions (wallet_id, trade_id, transaction_type, amount, balance_after, idempotency_key)
                    SELECT id, ?, 'PAYMENT', ?, ?, ? FROM wallet_accounts WHERE user_id = ?
                    """, trade.id(), trade.payableAmount().negate(), balance, "BALANCE-" + trade.tradeNo(), user.userId());
            return settlePaidTrade(user, tradeNo);
        } catch (RuntimeException exception) {
            userJdbcTemplate.update("UPDATE wallet_accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    trade.payableAmount(), user.userId());
            throw exception;
        }
    }

    private TradeView settlePaidTrade(CurrentUser user, String tradeNo) {
        TradeView trade = requireOwnedPendingTrade(user, tradeNo);
        if (trade.reservationExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(CONFLICT, "inventory reservation has expired");
        }
        List<Reservation> reservations = jdbcTemplate.query("""
                SELECT id, product_id, batch_id, reserved_grams FROM inventory_reservations
                WHERE trade_id = ? AND status = 'ACTIVE' FOR UPDATE
                """, (rs, row) -> new Reservation(rs.getLong("id"), rs.getLong("product_id"),
                rs.getLong("batch_id"), rs.getInt("reserved_grams")), trade.id());
        if (reservations.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "no active inventory reservation found");
        }
        for (Reservation reservation : reservations) {
            int updated = jdbcTemplate.update("""
                    UPDATE freshmart_merchant.inventory_batches
                    SET available_grams = available_grams - ?, reserved_grams = reserved_grams - ?
                    WHERE id = ? AND product_id = ? AND available_grams >= ? AND reserved_grams >= ?
                    """, reservation.grams(), reservation.grams(), reservation.batchId(), reservation.productId(),
                    reservation.grams(), reservation.grams());
            if (updated == 0) {
                throw new ResponseStatusException(CONFLICT, "reserved inventory is no longer available");
            }
        }
        jdbcTemplate.update("UPDATE inventory_reservations SET status = 'CONSUMED' WHERE trade_id = ? AND status = 'ACTIVE'", trade.id());
        jdbcTemplate.update("UPDATE trade_orders SET status = 'PAID' WHERE id = ?", trade.id());
        jdbcTemplate.update("UPDATE orders SET status = 'WAITING_PICKING' WHERE trade_id = ?", trade.id());
        jdbcTemplate.update("""
                INSERT INTO freshmart_delivery.delivery_tasks (order_id, merchant_id, warehouse_id, delivery_zone_id, status)
                SELECT id, merchant_id, warehouse_id, delivery_zone_id, 'WAITING_ASSIGNMENT'
                FROM orders WHERE trade_id = ?
                """, trade.id());
        jdbcTemplate.update("""
                UPDATE payment_orders SET status = 'PAID', paid_at = CURRENT_TIMESTAMP
                WHERE trade_id = ? AND status = 'PENDING'
                """, trade.id());
        jdbcTemplate.update("""
                INSERT INTO fee_ledgers (trade_id, fee_type, amount, direction, reference_type, reference_id, idempotency_key)
                VALUES (?, 'USER_PAYMENT', ?, 'CREDIT', 'TRADE', ?, ?)
                """, trade.id(), trade.payableAmount(), trade.tradeNo(), "PAYMENT-" + trade.tradeNo());
        userJdbcTemplate.update("""
                UPDATE user_coupons SET status = 'USED', used_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND used_trade_id = ? AND status = 'RESERVED'
                """, trade.userId(), trade.id());
        awardPoints(trade);
        return findById(trade.id());
    }

    private void awardPoints(TradeView trade) {
        int points = com.freshmart.marketing.PointsCalculator.awardablePoints(trade.payableAmount(), pointsPerCurrency);
        if (points <= 0) {
            return;
        }
        Integer balance = userJdbcTemplate.query("SELECT balance_after FROM points_transactions WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                (rs, row) -> rs.getInt(1), trade.userId()).stream().findFirst().orElse(0);
        int newBalance = Math.addExact(balance, points);
        userJdbcTemplate.update("""
                INSERT IGNORE INTO points_transactions (user_id, trade_id, change_amount, balance_after, reason, idempotency_key)
                VALUES (?, ?, ?, ?, 'TRADE_PAYMENT', ?)
                """, trade.userId(), trade.id(), points, newBalance, "POINTS-" + trade.tradeNo());
    }

    private boolean reserveCoupons(long userId, long tradeId, List<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return true;
        }
        Set<Long> unique = new HashSet<>(couponIds);
        if (unique.size() != couponIds.size()) {
            return false;
        }
        List<Long> reserved = new ArrayList<>();
        for (Long couponId : unique) {
            int updated = userJdbcTemplate.update("""
                    UPDATE user_coupons SET status = 'RESERVED', used_trade_id = ?
                    WHERE user_id = ? AND coupon_id = ? AND status = 'AVAILABLE'
                    """, tradeId, userId, couponId);
            if (updated == 0) {
                if (!reserved.isEmpty()) {
                    userJdbcTemplate.update("UPDATE user_coupons SET status = 'AVAILABLE', used_trade_id = NULL WHERE user_id = ? AND used_trade_id = ?",
                            userId, tradeId);
                }
                return false;
            }
            reserved.add(couponId);
        }
        return true;
    }

    private Map<Long, List<PromotionCalculator.Promotion>> loadPromotions(long userId, List<Long> couponIds,
            Set<Long> merchantIds) {
        Map<Long, List<PromotionCalculator.Promotion>> result = new HashMap<>();
        if (couponIds == null || couponIds.isEmpty()) {
            return result;
        }
        for (Long couponId : new HashSet<>(couponIds)) {
            boolean owned = !userJdbcTemplate.query("SELECT id FROM user_coupons WHERE user_id = ? AND coupon_id = ? AND status = 'AVAILABLE'",
                    (rs, row) -> rs.getLong(1), userId, couponId).isEmpty();
            if (!owned) {
                throw new ResponseStatusException(CONFLICT, "coupon is not available to this user");
            }
            List<Coupon> coupons = merchantJdbcTemplate.query("""
                    SELECT id, merchant_id, coupon_type, threshold_amount, discount_amount
                    FROM coupons WHERE id = ? AND status = 'ACTIVE' AND starts_at <= CURRENT_TIMESTAMP AND ends_at > CURRENT_TIMESTAMP
                    """, (rs, row) -> new Coupon(rs.getLong("id"), (Long) rs.getObject("merchant_id"),
                    rs.getString("coupon_type"), rs.getBigDecimal("threshold_amount"), rs.getBigDecimal("discount_amount")), couponId);
            Coupon coupon = coupons.stream().findFirst().orElseThrow(() -> new ResponseStatusException(CONFLICT, "coupon is expired or inactive"));
            if (coupon.merchantId() == null || !merchantIds.contains(coupon.merchantId())) {
                throw new ResponseStatusException(CONFLICT, "coupon does not match a merchant suborder");
            }
            result.computeIfAbsent(coupon.merchantId(), ignored -> new ArrayList<>())
                    .add(new PromotionCalculator.Promotion(coupon.couponType(), coupon.thresholdAmount(), coupon.discountAmount(), true, 100));
        }
        return result;
    }

    private ProductWarehouse findProductWarehouse(long productId, long deliveryZoneId) {
        return jdbcTemplate.query("""
                SELECT product.id, product.merchant_id, product.category_id, product.name, product.market_price_per_kg,
                       product.merchant_price_per_kg, rule.warehouse_id
                FROM freshmart_merchant.products product
                JOIN freshmart_merchant.merchants merchant ON merchant.id = product.merchant_id AND merchant.status = 'ACTIVE'
                JOIN freshmart_merchant.merchant_category_warehouse_rules rule
                  ON rule.merchant_id = product.merchant_id AND rule.category_id = product.category_id AND rule.status = 'ACTIVE'
                JOIN freshmart_merchant.warehouse_operable_categories category
                  ON category.warehouse_id = rule.warehouse_id AND category.category_id = rule.category_id AND category.status = 'ACTIVE'
                JOIN freshmart_merchant.warehouses warehouse
                  ON warehouse.id = rule.warehouse_id AND warehouse.status = 'ACTIVE' AND warehouse.delivery_zone_id = ?
                WHERE product.id = ? AND product.status = 'ACTIVE'
                ORDER BY rule.priority ASC, rule.warehouse_id ASC LIMIT 1
                """, (rs, row) -> new ProductWarehouse(rs.getLong("id"), rs.getLong("merchant_id"),
                rs.getLong("category_id"), rs.getLong("warehouse_id"), rs.getString("name"),
                rs.getBigDecimal("market_price_per_kg"), rs.getBigDecimal("merchant_price_per_kg")), deliveryZoneId, productId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "active product warehouse rule not found"));
    }

    private void reserveBatches(long tradeId, long orderId, long productId, long warehouseId, int grams, LocalDateTime expiresAt) {
        int remaining = grams;
        List<Batch> batches = jdbcTemplate.query("""
                SELECT id, available_grams, reserved_grams FROM freshmart_merchant.inventory_batches
                WHERE product_id = ? AND warehouse_id = ? AND available_grams > reserved_grams
                ORDER BY expires_on IS NULL, expires_on, id FOR UPDATE
                """, (rs, row) -> new Batch(rs.getLong("id"), rs.getInt("available_grams"), rs.getInt("reserved_grams")), productId, warehouseId);
        for (Batch batch : batches) {
            if (remaining == 0) {
                break;
            }
            int allocated = Math.min(remaining, batch.availableGrams() - batch.reservedGrams());
            int updated = jdbcTemplate.update("""
                    UPDATE freshmart_merchant.inventory_batches SET reserved_grams = reserved_grams + ?
                    WHERE id = ? AND available_grams - reserved_grams >= ?
                    """, allocated, batch.id(), allocated);
            if (updated == 0) {
                throw new ResponseStatusException(CONFLICT, "inventory changed while reserving");
            }
            jdbcTemplate.update("""
                    INSERT INTO inventory_reservations (trade_id, order_id, product_id, batch_id, reserved_grams, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, tradeId, orderId, productId, batch.id(), allocated, expiresAt);
            remaining -= allocated;
        }
        if (remaining > 0) {
            throw new ResponseStatusException(CONFLICT, "insufficient inventory");
        }
    }

    private TradeView requireOwnedPendingTrade(CurrentUser user, String tradeNo) {
        TradeView trade = findByTradeNo(tradeNo);
        if (trade == null) {
            throw new ResponseStatusException(NOT_FOUND, "trade not found");
        }
        if (trade.userId() != user.userId()) {
            throw new ResponseStatusException(FORBIDDEN, "trade is not owned by this user");
        }
        if (!"PENDING_PAYMENT".equals(trade.status())) {
            throw new ResponseStatusException(CONFLICT, "trade is not awaiting payment");
        }
        return trade;
    }

    private TradeView findById(long id) {
        return queryTrade("SELECT * FROM trade_orders WHERE id = ?", id);
    }

    private TradeView findByTradeNo(String tradeNo) {
        return queryTrade("SELECT * FROM trade_orders WHERE trade_no = ?", tradeNo);
    }

    private TradeView findByIdempotencyKey(String idempotencyKey) {
        return queryTrade("SELECT * FROM trade_orders WHERE idempotency_key = ?", idempotencyKey);
    }

    private TradeView queryTrade(String sql, Object value) {
        return jdbcTemplate.query(sql, (rs, row) -> new TradeView(rs.getLong("id"), rs.getString("trade_no"),
                rs.getLong("user_id"), rs.getString("status"), rs.getBigDecimal("goods_amount"),
                rs.getBigDecimal("freight_amount"), rs.getBigDecimal("discount_amount"), rs.getBigDecimal("payable_amount"),
                rs.getObject("reservation_expires_at", LocalDateTime.class), List.of()), value).stream().findFirst().orElse(null);
    }

    private long lastInsertId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String newNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "snapshot cannot be serialized");
        }
    }

    public record CheckoutLine(long productId, int weightGrams) {
    }

    public record TradeView(long id, String tradeNo, long userId, String status, BigDecimal goodsAmount,
            BigDecimal freightAmount, BigDecimal discountAmount, BigDecimal payableAmount, LocalDateTime reservationExpiresAt, List<String> orderNos) {
    }

    public record PaymentView(String paymentNo, String status, BigDecimal amount, String codeUrl) {
    }

    private record ProductWarehouse(long productId, long merchantId, long categoryId, long warehouseId, String name,
            BigDecimal marketPricePerKg, BigDecimal merchantPricePerKg) {
    }

    private record PricedLine(ProductWarehouse product, int weightGrams, MarketPriceSettlementPolicy.PriceBreakdown price) {
    }

    private record Batch(long id, int availableGrams, int reservedGrams) {
    }

    private record Reservation(long id, long productId, long batchId, int grams) {
    }

    private record Coupon(long id, Long merchantId, String couponType, BigDecimal thresholdAmount, BigDecimal discountAmount) {
    }
}
