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

    /**
     * 用户侧预览：只渲染卡片外观，不产生站内消息、不落投递记录，因此不写审计。
     * 未传 holidayKey / greeting 时给出一份可直接展示的默认样例。
     */
    public PreviewView preview(String holidayKey, String greeting) {
        String safeHoliday = HolidayCardContentPolicy.sanitize(
                holidayKey == null || holidayKey.isBlank() ? "节气" : holidayKey, 32);
        String safeGreeting = HolidayCardContentPolicy.sanitize(
                greeting == null || greeting.isBlank() ? "节气将至，愿新鲜常伴" : greeting, 120);
        return new PreviewView(safeHoliday, safeGreeting,
                HolidayCardContentPolicy.render(safeHoliday, safeGreeting));
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

    /** svg 为可直接内联展示的卡片内容，已按 HolidayCardContentPolicy 做过转义 */
    public record PreviewView(String holidayKey, String greeting, String svg) {
    }
}
