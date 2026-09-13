package com.freshmart.notification;

import com.freshmart.auth.AuditLogService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class HolidayCardTaskService {
    private final JdbcTemplate logJdbcTemplate;
    private final JdbcTemplate userJdbcTemplate;
    private final HolidayCardService holidayCardService;
    private final AuditLogService auditLogService;

    public HolidayCardTaskService(@Qualifier("logJdbcTemplate") JdbcTemplate logJdbcTemplate,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate, HolidayCardService holidayCardService,
            AuditLogService auditLogService) {
        this.logJdbcTemplate = logJdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
        this.holidayCardService = holidayCardService;
        this.auditLogService = auditLogService;
    }

    public TaskView create(long operatorId, String taskKey, String holidayKey, String greeting, LocalDateTime scheduledAt,
            String sourceIp) {
        String cleanTaskKey = HolidayCardContentPolicy.sanitize(taskKey, 64);
        String cleanHolidayKey = HolidayCardContentPolicy.sanitize(holidayKey, 32);
        String cleanGreeting = HolidayCardContentPolicy.sanitize(greeting, 120);
        if (cleanTaskKey.isBlank() || cleanHolidayKey.isBlank() || cleanGreeting.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "holiday card task content is invalid");
        }
        try {
            logJdbcTemplate.update("""
                    INSERT INTO holiday_card_tasks (task_key, holiday_key, greeting, scheduled_at, status, created_by)
                    VALUES (?, ?, ?, ?, 'PENDING', ?)
                    """, cleanTaskKey, cleanHolidayKey, cleanGreeting, scheduledAt, operatorId);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(CONFLICT, "holiday card task key already exists");
        }
        TaskView task = findByTaskKey(cleanTaskKey);
        auditLogService.record(operatorId, "HOLIDAY_CARD_TASK_CREATED", "HOLIDAY_CARD_TASK", Long.toString(task.id()), sourceIp);
        return task;
    }

    public List<TaskView> list() {
        return logJdbcTemplate.query("""
                SELECT id, task_key, holiday_key, greeting, scheduled_at, status, created_by, completed_at, sent_count, failed_count, created_at
                FROM holiday_card_tasks ORDER BY scheduled_at DESC, id DESC LIMIT 100
                """, (rs, row) -> mapTask(rs));
    }

    public TaskView runNow(long taskId, long operatorId, String sourceIp) {
        TaskView result = run(taskId);
        auditLogService.record(operatorId, "HOLIDAY_CARD_TASK_RUN", "HOLIDAY_CARD_TASK", Long.toString(taskId), sourceIp);
        return result;
    }

    public void dispatchDueTasks() {
        List<Long> taskIds = logJdbcTemplate.query("""
                SELECT id FROM holiday_card_tasks
                WHERE status IN ('PENDING', 'FAILED') AND scheduled_at <= CURRENT_TIMESTAMP
                ORDER BY scheduled_at, id LIMIT 20
                """, (rs, row) -> rs.getLong(1));
        taskIds.forEach(this::runScheduled);
    }

    private void runScheduled(long taskId) {
        try {
            run(taskId);
        } catch (RuntimeException ignored) {
        }
    }

    private TaskView run(long taskId) {
        TaskView task = find(taskId);
        int started = logJdbcTemplate.update("""
                UPDATE holiday_card_tasks SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('PENDING', 'FAILED')
                """, taskId);
        if (started == 0) {
            throw new ResponseStatusException(CONFLICT, "holiday card task is already running");
        }
        try {
            List<Long> recipientIds = activeOptedInConsumerIds();
            for (Long userId : recipientIds) {
                dispatchToRecipient(task, userId);
            }
            refreshTaskCounts(taskId);
            return find(taskId);
        } catch (RuntimeException exception) {
            logJdbcTemplate.update("UPDATE holiday_card_tasks SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP WHERE id = ?", taskId);
            throw exception;
        }
    }

    private void dispatchToRecipient(TaskView task, long userId) {
        logJdbcTemplate.update("""
                INSERT INTO holiday_card_deliveries (task_id, user_id, status)
                VALUES (?, ?, 'PENDING')
                ON DUPLICATE KEY UPDATE
                    status = IF(status = 'FAILED', 'PENDING', status),
                    last_error = IF(status = 'FAILED', NULL, last_error)
                """, task.id(), userId);
        int claimed = logJdbcTemplate.update("""
                UPDATE holiday_card_deliveries
                SET status = 'PROCESSING', attempts = attempts + 1, last_error = NULL
                WHERE task_id = ? AND user_id = ? AND status IN ('PENDING', 'FAILED')
                """, task.id(), userId);
        if (claimed == 0) {
            return;
        }
        try {
            InboxService.MessageView message = holidayCardService.sendScheduled(task.createdBy(), task.id(), userId,
                    task.taskKey(), task.holidayKey(), task.greeting());
            logJdbcTemplate.update("""
                    UPDATE holiday_card_deliveries
                    SET status = 'SENT', inbox_message_id = ?, sent_at = CURRENT_TIMESTAMP, last_error = NULL
                    WHERE task_id = ? AND user_id = ?
                    """, message.id(), task.id(), userId);
        } catch (RuntimeException exception) {
            logJdbcTemplate.update("""
                    UPDATE holiday_card_deliveries SET status = 'FAILED', last_error = ?
                    WHERE task_id = ? AND user_id = ?
                    """, compactError(exception), task.id(), userId);
        }
    }

    private List<Long> activeOptedInConsumerIds() {
        return userJdbcTemplate.query("""
                SELECT DISTINCT users.id
                FROM users
                JOIN user_role_assignments roles ON roles.user_id = users.id AND roles.role_code = 'CONSUMER'
                LEFT JOIN user_notification_preferences preferences ON preferences.user_id = users.id
                WHERE users.status = 'ACTIVE'
                  AND COALESCE(preferences.seasonal_card_enabled, TRUE) = TRUE
                  AND EXISTS (
                      SELECT 1 FROM auth_sessions sessions
                      WHERE sessions.user_id = users.id AND sessions.revoked_at IS NULL
                        AND sessions.expires_at > CURRENT_TIMESTAMP
                        AND sessions.last_seen_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
                  )
                """, (rs, row) -> rs.getLong(1));
    }

    private void refreshTaskCounts(long taskId) {
        Counts counts = logJdbcTemplate.query("""
                SELECT SUM(status = 'SENT'), SUM(status = 'FAILED')
                FROM holiday_card_deliveries WHERE task_id = ?
                """, (rs, row) -> new Counts(rs.getInt(1), rs.getInt(2)), taskId).stream()
                .findFirst().orElse(new Counts(0, 0));
        String status = counts.failed() > 0 ? "FAILED" : "COMPLETED";
        logJdbcTemplate.update("""
                UPDATE holiday_card_tasks
                SET status = ?, completed_at = CURRENT_TIMESTAMP, sent_count = ?, failed_count = ?
                WHERE id = ?
                """, status, counts.sent(), counts.failed(), taskId);
    }

    private TaskView find(long taskId) {
        return logJdbcTemplate.query("""
                SELECT id, task_key, holiday_key, greeting, scheduled_at, status, created_by, completed_at, sent_count, failed_count, created_at
                FROM holiday_card_tasks WHERE id = ?
                """, (rs, row) -> mapTask(rs), taskId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "holiday card task not found"));
    }

    private TaskView findByTaskKey(String taskKey) {
        return logJdbcTemplate.query("""
                SELECT id, task_key, holiday_key, greeting, scheduled_at, status, created_by, completed_at, sent_count, failed_count, created_at
                FROM holiday_card_tasks WHERE task_key = ?
                """, (rs, row) -> mapTask(rs), taskKey).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "holiday card task not found"));
    }

    private TaskView mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskView(rs.getLong("id"), rs.getString("task_key"), rs.getString("holiday_key"),
                rs.getString("greeting"), rs.getObject("scheduled_at", LocalDateTime.class), rs.getString("status"),
                rs.getLong("created_by"), rs.getObject("completed_at", LocalDateTime.class), rs.getInt("sent_count"),
                rs.getInt("failed_count"), rs.getObject("created_at", LocalDateTime.class));
    }

    private String compactError(RuntimeException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.substring(0, Math.min(message.length(), 500));
    }

    private record Counts(int sent, int failed) {
    }

    public record TaskView(long id, String taskKey, String holidayKey, String greeting, LocalDateTime scheduledAt,
            String status, long createdBy, LocalDateTime completedAt, int sentCount, int failedCount,
            LocalDateTime createdAt) {
    }
}
