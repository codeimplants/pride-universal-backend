package com.prideuniversal.domain.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {}

    /** A phone announcing itself after sign-in, and again when the app language changes. */
    public record DeviceRequest(
            @NotBlank(message = "Push token is required")
            @Pattern(regexp = "^Expo(nent)?PushToken\\[[^\\]]+\\]$", message = "Not an Expo push token") String token,
            @NotBlank(message = "Platform is required")
            @Pattern(regexp = "^(android|ios)$", message = "Platform must be android or ios") String platform,
            @Size(max = 120, message = "Device name is too long") String deviceName,
            @Pattern(regexp = "^(en|mr|hi)$", message = "Language must be en, mr or hi") String language
    ) {}

    /**
     * The committee writing to members. {@code audience}: {@code all}, {@code wing} (value A or B),
     * {@code flat} (value "A-101"), {@code committee}, {@code security} or {@code user} (value an
     * account id). {@code screen} is where a tap opens the app.
     */
    public record ComposeRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 120, message = "Title is too long") String title,
            @NotBlank(message = "Message is required")
            @Size(max = 1_000, message = "Message is too long") String body,
            @NotBlank(message = "Choose who receives it")
            @Pattern(regexp = "^(all|wing|flat|committee|security|user)$", message = "Unknown audience") String audience,
            @Size(max = 64, message = "Audience is too long") String audienceValue,
            @Pattern(regexp = "^[a-z-]+(:[0-9a-f-]{36})?$", message = "Unknown screen")
            @Size(max = 64, message = "Screen is too long") String screen
    ) {}

    public record ReadRequest(List<UUID> ids) {}

    /** What happened: accounts it reached, phones it was sent to, and phones that took it. */
    public record SendResult(int recipients, int devices, int delivered, List<String> errors) {}

    public record DuesResult(int flats, int recipients, int devices, int delivered) {}

    /** One inbox row. {@code params} is set for the kinds the app writes itself. */
    public record InboxItem(
            UUID id,
            String kind,
            String title,
            String body,
            Map<String, Object> params,
            String screen,
            String sentBy,
            Instant createdDate,
            boolean read
    ) {}

    /** The committee's record of what went out. */
    public record SentItem(
            UUID id,
            String kind,
            String title,
            String body,
            String audience,
            String audienceValue,
            String sentBy,
            int recipients,
            int devices,
            int delivered,
            Instant createdDate
    ) {
        static SentItem from(Notification n) {
            return new SentItem(n.getId(), n.getKind(), n.getTitle(), n.getBody(), n.getAudience(), n.getAudienceValue(),
                    n.getSentBy(), n.getRecipients(), n.getDevices(), n.getDelivered(), n.getCreatedDate());
        }
    }
}
