package com.freshmart.merchant;

import com.freshmart.auth.AuditLogService;
import com.freshmart.auth.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MerchantApplicationService {
    private final JdbcTemplate merchantJdbcTemplate;
    private final JdbcTemplate userJdbcTemplate;
    private final AuditLogService auditLogService;

    public MerchantApplicationService(
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate,
            AuditLogService auditLogService) {
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional("merchantTransactionManager")
    public ApplicationView submit(CurrentUser user, String merchantName, String businessLicenseUrl, String sourceIp) {
        if (!user.hasRole("CONSUMER")) {
            throw new ResponseStatusException(BAD_REQUEST, "only consumer accounts can submit a merchant application");
        }
        Long pendingCount = merchantJdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM merchant_applications application
                JOIN merchants merchant ON merchant.id = application.merchant_id
                WHERE application.applicant_user_id = ? AND application.status = 'PENDING'
                """, Long.class, user.userId());
        if (pendingCount != null && pendingCount > 0) {
            throw new ResponseStatusException(CONFLICT, "a merchant application is already pending review");
        }
        merchantJdbcTemplate.update("INSERT INTO merchants (owner_user_id, name, status) VALUES (?, ?, 'PENDING')",
                user.userId(), merchantName);
        Long merchantId = merchantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        merchantJdbcTemplate.update("""
                INSERT INTO merchant_applications (merchant_id, applicant_user_id, business_license_url)
                VALUES (?, ?, ?)
                """, merchantId, user.userId(), businessLicenseUrl);
        auditLogService.record(user.userId(), "MERCHANT_APPLICATION_SUBMITTED", "MERCHANT", merchantId.toString(), sourceIp);
        return findByMerchantId(merchantId);
    }

    public List<ApplicationView> list(String status) {
        return merchantJdbcTemplate.query("""
                SELECT application.id, application.merchant_id, merchant.name AS merchant_name,
                       application.applicant_user_id, application.business_license_url, application.status,
                       application.review_note, application.reviewed_by, application.reviewed_at, application.created_at
                FROM merchant_applications application JOIN merchants merchant ON merchant.id = application.merchant_id
                WHERE (? IS NULL OR application.status = ?)
                ORDER BY application.created_at ASC
                """, (resultSet, rowNum) -> new ApplicationView(
                resultSet.getLong("id"), resultSet.getLong("merchant_id"), resultSet.getString("merchant_name"),
                resultSet.getLong("applicant_user_id"), resultSet.getString("business_license_url"),
                resultSet.getString("status"), resultSet.getString("review_note"),
                (Long) resultSet.getObject("reviewed_by"), resultSet.getObject("reviewed_at", LocalDateTime.class),
                resultSet.getObject("created_at", LocalDateTime.class)), status, status);
    }

    @Transactional("merchantTransactionManager")
    public ApplicationView review(CurrentUser admin, Long merchantId, boolean approved, String reviewNote, String sourceIp) {
        ApplicationView application = findPendingByMerchantId(merchantId);
        String applicationStatus = approved ? "APPROVED" : "REJECTED";
        String merchantStatus = approved ? "ACTIVE" : "REJECTED";
        merchantJdbcTemplate.update("""
                UPDATE merchant_applications SET status = ?, review_note = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, applicationStatus, reviewNote, admin.userId(), application.applicationId());
        merchantJdbcTemplate.update("UPDATE merchants SET status = ? WHERE id = ?", merchantStatus, merchantId);
        if (approved) {
            userJdbcTemplate.update("INSERT IGNORE INTO user_role_assignments (user_id, role_code) VALUES (?, 'MERCHANT')",
                    application.applicantUserId());
        }
        auditLogService.record(admin.userId(), approved ? "MERCHANT_APPLICATION_APPROVED" : "MERCHANT_APPLICATION_REJECTED",
                "MERCHANT", merchantId.toString(), sourceIp);
        return findByMerchantId(merchantId);
    }

    private ApplicationView findPendingByMerchantId(Long merchantId) {
        return merchantJdbcTemplate.query("""
                SELECT application.id, application.merchant_id, merchant.name AS merchant_name,
                       application.applicant_user_id, application.business_license_url, application.status,
                       application.review_note, application.reviewed_by, application.reviewed_at, application.created_at
                FROM merchant_applications application JOIN merchants merchant ON merchant.id = application.merchant_id
                WHERE application.merchant_id = ? AND application.status = 'PENDING'
                """, (resultSet, rowNum) -> new ApplicationView(
                resultSet.getLong("id"), resultSet.getLong("merchant_id"), resultSet.getString("merchant_name"),
                resultSet.getLong("applicant_user_id"), resultSet.getString("business_license_url"),
                resultSet.getString("status"), resultSet.getString("review_note"),
                (Long) resultSet.getObject("reviewed_by"), resultSet.getObject("reviewed_at", LocalDateTime.class),
                resultSet.getObject("created_at", LocalDateTime.class)), merchantId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "pending merchant application not found"));
    }

    private ApplicationView findByMerchantId(Long merchantId) {
        return merchantJdbcTemplate.query("""
                SELECT application.id, application.merchant_id, merchant.name AS merchant_name,
                       application.applicant_user_id, application.business_license_url, application.status,
                       application.review_note, application.reviewed_by, application.reviewed_at, application.created_at
                FROM merchant_applications application JOIN merchants merchant ON merchant.id = application.merchant_id
                WHERE application.merchant_id = ? ORDER BY application.created_at DESC LIMIT 1
                """, (resultSet, rowNum) -> new ApplicationView(
                resultSet.getLong("id"), resultSet.getLong("merchant_id"), resultSet.getString("merchant_name"),
                resultSet.getLong("applicant_user_id"), resultSet.getString("business_license_url"),
                resultSet.getString("status"), resultSet.getString("review_note"),
                (Long) resultSet.getObject("reviewed_by"), resultSet.getObject("reviewed_at", LocalDateTime.class),
                resultSet.getObject("created_at", LocalDateTime.class)), merchantId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "merchant application not found"));
    }

    public record ApplicationView(
            Long applicationId,
            Long merchantId,
            String merchantName,
            Long applicantUserId,
            String businessLicenseUrl,
            String status,
            String reviewNote,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt) {
    }
}
