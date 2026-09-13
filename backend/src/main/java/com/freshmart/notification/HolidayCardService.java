package com.freshmart.notification;

import com.freshmart.auth.AuditLogService;
import org.springframework.stereotype.Service;

@Service
public class HolidayCardService {
    private final InboxService inboxService;
    private final AuditLogService auditLogService;

    public HolidayCardService(InboxService inboxService, AuditLogService auditLogService) {
        this.inboxService = inboxService;
        this.auditLogService = auditLogService;
    }

    public InboxService.MessageView send(long operatorId, long userId, String holidayKey, String greeting,
            String sourceIp) {
        String safeHoliday = HolidayCardContentPolicy.sanitize(holidayKey, 32);
        String safeGreeting = HolidayCardContentPolicy.sanitize(greeting, 120);
        String svg = HolidayCardContentPolicy.render(safeHoliday, safeGreeting);
        InboxService.MessageView message = inboxService.sendWithSvg(userId, "HOLIDAY_CARD", safeHoliday, safeGreeting,
                "local://holiday-card/" + userId + "/" + System.currentTimeMillis(), svg, "HOLIDAY", null);
        auditLogService.record(operatorId, "HOLIDAY_CARD_SENT", "INBOX_MESSAGE", Long.toString(message.id()), sourceIp);
        return message;
    }

    public InboxService.MessageView sendScheduled(long operatorId, long taskId, long userId, String taskKey,
            String holidayKey, String greeting) {
        String safeHoliday = HolidayCardContentPolicy.sanitize(holidayKey, 32);
        String safeGreeting = HolidayCardContentPolicy.sanitize(greeting, 120);
        InboxService.MessageView message = inboxService.sendWithSvgIdempotently(userId, "HOLIDAY_CARD", safeHoliday,
                safeGreeting, "local://holiday-card/task/" + taskId + "/" + userId,
                HolidayCardContentPolicy.render(safeHoliday, safeGreeting), "HOLIDAY_TASK", taskId,
                "HOLIDAY-CARD-" + taskKey + "-" + userId);
        auditLogService.record(operatorId, "HOLIDAY_CARD_SENT", "HOLIDAY_CARD_TASK", Long.toString(taskId), "SYSTEM");
        return message;
    }
}
