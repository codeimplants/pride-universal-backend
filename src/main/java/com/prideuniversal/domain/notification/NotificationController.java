package com.prideuniversal.domain.notification;

import com.prideuniversal.domain.notification.NotificationDtos.ComposeRequest;
import com.prideuniversal.domain.notification.NotificationDtos.DeviceRequest;
import com.prideuniversal.domain.notification.NotificationDtos.DuesResult;
import com.prideuniversal.domain.notification.NotificationDtos.InboxItem;
import com.prideuniversal.domain.notification.NotificationDtos.ReadRequest;
import com.prideuniversal.domain.notification.NotificationDtos.SendResult;
import com.prideuniversal.domain.notification.NotificationDtos.SentItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Notifications", description = "Push to members' phones, and the in-app inbox")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/api/push/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Register this phone for push (after sign-in, and when the app language changes)")
    public void register(@Valid @RequestBody DeviceRequest request) {
        service.registerDevice(request);
    }

    @DeleteMapping("/api/push/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stop push to this phone (on sign-out)")
    public void unregister(@RequestParam String token) {
        service.unregisterDevice(token);
    }

    @GetMapping("/api/notifications")
    @Operation(summary = "My inbox, newest first")
    public List<InboxItem> inbox() {
        return service.inbox();
    }

    @PostMapping("/api/notifications/read")
    @Operation(summary = "Mark these notifications read, or all of them when no ids are sent")
    public Map<String, Integer> read(@RequestBody(required = false) ReadRequest request) {
        return Map.of("updated", service.markRead(request));
    }

    @PostMapping("/api/notifications")
    @Operation(summary = "Send a notification to everyone, a wing, a flat, the committee or the guards")
    public SendResult send(@Valid @RequestBody ComposeRequest request) {
        return service.send(request);
    }

    @PostMapping("/api/notifications/dues-reminders")
    @Operation(summary = "Remind every flat with unpaid maintenance how much it owes")
    public DuesResult duesReminders() {
        return service.sendDuesReminders();
    }

    @GetMapping("/api/notifications/sent")
    @Operation(summary = "What the committee has sent, with how many phones took it")
    public List<SentItem> sent() {
        return service.sent();
    }
}
