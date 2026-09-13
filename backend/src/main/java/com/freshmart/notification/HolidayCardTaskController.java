package com.freshmart.notification;

import com.freshmart.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class HolidayCardTaskController {
    private final HolidayCardTaskService taskService;

    public HolidayCardTaskController(HolidayCardTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/admin/notification/holiday-card-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayCardTaskService.TaskView create(@AuthenticationPrincipal CurrentUser admin,
            @Valid @RequestBody CreateRequest request, HttpServletRequest servletRequest) {
        return taskService.create(admin.userId(), request.taskKey(), request.holidayKey(), request.greeting(),
                request.scheduledAt(), sourceIp(servletRequest));
    }

    @GetMapping("/api/admin/notification/holiday-card-tasks")
    public List<HolidayCardTaskService.TaskView> list() {
        return taskService.list();
    }

    @PostMapping("/api/admin/notification/holiday-card-tasks/{taskId}/run")
    public HolidayCardTaskService.TaskView run(@AuthenticationPrincipal CurrentUser admin, @PathVariable long taskId,
            HttpServletRequest servletRequest) {
        return taskService.runNow(taskId, admin.userId(), sourceIp(servletRequest));
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null ? request.getRemoteAddr() : forwardedFor.split(",")[0].trim();
    }

    public record CreateRequest(@NotBlank @Size(max = 64) String taskKey, @NotBlank @Size(max = 32) String holidayKey,
            @NotBlank @Size(max = 120) String greeting, @NotNull @FutureOrPresent LocalDateTime scheduledAt) {
    }
}
