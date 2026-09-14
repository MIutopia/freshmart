package com.freshmart.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HolidayCardTaskJob {
    private final HolidayCardTaskService taskService;

    public HolidayCardTaskJob(HolidayCardTaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${commerce.notification.seasonal-card-scan-interval-ms:60000}")
    public void dispatchDueTasks() {
        taskService.dispatchDueTasks();
    }
}
