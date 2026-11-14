package com.prideuniversal.domain.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.common.Text;
import com.prideuniversal.domain.bill.BillStatus;
import com.prideuniversal.domain.bill.MaintenanceBillRepository;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.notification.NotificationDtos.ComposeRequest;
import com.prideuniversal.domain.notification.NotificationDtos.DeviceRequest;
import com.prideuniversal.domain.notification.NotificationDtos.DuesResult;
import com.prideuniversal.domain.notification.NotificationDtos.InboxItem;
import com.prideuniversal.domain.notification.NotificationDtos.ReadRequest;
import com.prideuniversal.domain.notification.NotificationDtos.SendResult;
import com.prideuniversal.domain.notification.NotificationDtos.SentItem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Notifications: the committee writing to members, and the society's own dues reminders.
 *
 * <p>Each send happens in three steps so that no database connection is held while Expo is called:
 * record the notification and its recipients (one transaction), push to their phones (no transaction),
 * then note how many phones took it and forget tokens Expo says are dead (a second transaction).
 * The inbox never depends on the push: a member without push, or on the web, still finds it there.
 *
 * <ul>
 *   <li>Sending to members: admin, chairman, secretary.</li>
 *   <li>Dues reminders: those, and the treasurer.</li>
 *   <li>Every signed-in account registers its phones and reads its own inbox.</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final String SENDERS = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY')";
    private static final String REMINDERS = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER')";
    private static final EnumSet<Role> COMMITTEE =
            EnumSet.of(Role.ADMIN, Role.CHAIRMAN, Role.SECRETARY, Role.TREASURER, Role.COMMITTEE_MEMBER);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Dues reminder title and body per language: flat, amount, bill count. */
    private static final Map<String, String[]> DUES = Map.of(
            "en", new String[] {"Maintenance due",
                    "₹%2$s is due for flat %1$s (%3$d %4$s). Please pay at the earliest. Tap to see the details."},
            "mr", new String[] {"मेंटेनन्स बाकी",
                    "फ्लॅट %1$s साठी ₹%2$s मेंटेनन्स बाकी आहे (%3$d बिल). कृपया लवकरात लवकर भरणा करा."},
            "hi", new String[] {"मेंटेनेंस बकाया",
                    "फ्लैट %1$s के लिए ₹%2$s मेंटेनेंस बकाया है (%3$d बिल)। कृपया जल्द से जल्द भुगतान करें।"});

    private final PushDeviceRepository devices;
    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;
    private final RecipientQueries audiences;
    private final FlatRepository flats;
    private final MaintenanceBillRepository bills;
    private final ExpoPushClient push;
    private final TransactionTemplate tx;

    public NotificationService(PushDeviceRepository devices, NotificationRepository notifications,
                               NotificationRecipientRepository recipients, RecipientQueries audiences,
                               FlatRepository flats, MaintenanceBillRepository bills, ExpoPushClient push,
                               PlatformTransactionManager transactionManager) {
        this.devices = devices;
        this.notifications = notifications;
        this.recipients = recipients;
        this.audiences = audiences;
        this.flats = flats;
        this.bills = bills;
        this.push = push;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // =====================================================================
    // Phones
    // =====================================================================

    /** A token is one install; it moves to whoever signs in on that phone. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void registerDevice(DeviceRequest r) {
        PushDevice d = devices.findByToken(r.token()).orElseGet(PushDevice::new);
        d.setToken(r.token());
        d.setUserId(AuthPrincipal.current().userId());
        d.setPlatform(r.platform());
        d.setDeviceName(Text.blankToNull(r.deviceName()));
        d.setLanguage(r.language() == null ? "en" : r.language());
        d.setLastSeenAt(Instant.now());
        devices.save(d);
    }

    /** Only the phone's owner removes it (on sign-out), so one member cannot silence another. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void unregisterDevice(String token) {
        devices.deleteByTokenAndUserId(token, AuthPrincipal.current().userId());
    }

    // =====================================================================
    // Inbox
    // =====================================================================

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<InboxItem> inbox() {
        return recipients.inbox(AuthPrincipal.current().userId()).stream().map(row -> {
            NotificationRecipient r = (NotificationRecipient) row[0];
            Notification n = (Notification) row[1];
            return new InboxItem(n.getId(), n.getKind(), n.getTitle(), n.getBody(), params(n), n.getScreen(),
                    n.getSentBy(), n.getCreatedDate(), r.getReadAt() != null);
        }).toList();
    }

    /** The given notifications, or with no ids every unread one. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public int markRead(ReadRequest request) {
        UUID me = AuthPrincipal.current().userId();
        List<UUID> ids = request == null ? null : request.ids();
        return ids == null || ids.isEmpty()
                ? recipients.markAllRead(me, Instant.now())
                : recipients.markRead(me, ids, Instant.now());
    }

    @PreAuthorize(REMINDERS)
    @Transactional(readOnly = true)
    public List<SentItem> sent() {
        return notifications.latest().stream().map(SentItem::from).toList();
    }

    // =====================================================================
    // Sending
    // =====================================================================

    private record Prepared(UUID id, int recipients, List<ExpoPushClient.Message> messages) {}

    @PreAuthorize(SENDERS)
    public SendResult send(ComposeRequest r) {
        AuthPrincipal caller = AuthPrincipal.current();
        Prepared p = tx.execute(status -> {
            String value = normalisedValue(r.audience(), r.audienceValue());
            List<UUID> users = resolve(r.audience(), value);
            if (users.isEmpty()) {
                throw ApiException.badRequest("No one in that audience has signed in to the app yet.");
            }
            Notification n = new Notification();
            n.setKind(Notification.MESSAGE);
            n.setTitle(r.title().trim());
            n.setBody(r.body().trim());
            n.setAudience(r.audience());
            n.setAudienceValue(value);
            n.setScreen(Text.blankToNull(r.screen()));
            n.setSentBy(caller.displayName());
            n.setSentById(caller.userId());
            n.setRecipients(users.size());
            notifications.saveAndFlush(n);
            recipients.saveAll(users.stream().map(u -> NotificationRecipient.of(n.getId(), u)).toList());
            List<ExpoPushClient.Message> messages = devices.findAllByUserIdIn(users).stream()
                    .map(d -> new ExpoPushClient.Message(d.getToken(), n.getTitle(), n.getBody(), data(n)))
                    .toList();
            return new Prepared(n.getId(), users.size(), messages);
        });

        ExpoPushClient.Result result = push.send(p.messages());
        tx.executeWithoutResult(status -> {
            notifications.findById(p.id()).ifPresent(n -> {
                n.setDevices(p.messages().size());
                n.setDelivered(result.sent());
            });
            forgetDead(result);
        });
        return new SendResult(p.recipients(), p.messages().size(), result.sent(),
                result.errors().stream().distinct().limit(3).toList());
    }

    /**
     * One reminder per flat with unpaid bills, to every account on that flat, saying how much is due.
     * The push text is written in each phone's language; the inbox writes it in the reader's.
     */
    @PreAuthorize(REMINDERS)
    public DuesResult sendDuesReminders() {
        AuthPrincipal caller = AuthPrincipal.current();
        record FlatReminder(UUID notificationId, int from, int to) {}
        List<ExpoPushClient.Message> messages = new ArrayList<>();
        List<FlatReminder> sentToFlats = new ArrayList<>();
        int[] people = {0};

        tx.executeWithoutResult(status -> {
            for (Object[] row : bills.unpaidByFlat(List.of(BillStatus.PENDING, BillStatus.OVERDUE))) {
                Flat flat = flats.findById((UUID) row[0]).filter(Flat::isActive).orElse(null);
                if (flat == null) {
                    continue;
                }
                List<UUID> users = audiences.activeInFlat(flat.getId());
                if (users.isEmpty()) {
                    continue;   // nobody from this flat has signed in yet
                }
                String flatLabel = flat.getWing() + "-" + flat.getFlatNumber();
                int count = ((Number) row[1]).intValue();
                String amount = indian((BigDecimal) row[2]);

                Notification n = new Notification();
                n.setKind(Notification.DUES_REMINDER);
                String[] en = text("en", flatLabel, amount, count);
                n.setTitle(en[0]);
                n.setBody(en[1]);
                n.setParams(json(Map.of("flat", flatLabel, "amount", amount, "bills", count)));
                n.setAudience("flat");
                n.setAudienceValue(flatLabel);
                n.setScreen("my-maintenance");
                n.setSentBy(caller.displayName());
                n.setSentById(caller.userId());
                n.setRecipients(users.size());
                notifications.saveAndFlush(n);
                recipients.saveAll(users.stream().map(u -> NotificationRecipient.of(n.getId(), u)).toList());
                people[0] += users.size();

                int from = messages.size();
                for (PushDevice d : devices.findAllByUserIdIn(users)) {
                    String[] t = text(d.getLanguage(), flatLabel, amount, count);
                    messages.add(new ExpoPushClient.Message(d.getToken(), t[0], t[1], data(n)));
                }
                sentToFlats.add(new FlatReminder(n.getId(), from, messages.size()));
            }
        });

        ExpoPushClient.Result result = push.send(messages);
        tx.executeWithoutResult(status -> {
            for (FlatReminder f : sentToFlats) {
                int delivered = (int) result.ok().subList(f.from(), f.to()).stream().filter(Boolean::booleanValue).count();
                notifications.findById(f.notificationId()).ifPresent(n -> {
                    n.setDevices(f.to() - f.from());
                    n.setDelivered(delivered);
                });
            }
            forgetDead(result);
        });
        return new DuesResult(sentToFlats.size(), people[0], messages.size(), result.sent());
    }

    // =====================================================================
    // Internals
    // =====================================================================

    /** Upper-case wing, "A-101" for a flat; refuses values the audience cannot use. */
    private String normalisedValue(String audience, String raw) {
        String v = Text.blankToNull(raw);
        return switch (audience) {
            case "wing", "flat" -> {
                if (v == null) {
                    throw ApiException.badRequest("Say which " + audience + ".");
                }
                yield v.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            }
            case "user" -> {
                if (v == null) {
                    throw ApiException.badRequest("Say which account.");
                }
                yield v;
            }
            default -> null;
        };
    }

    private List<UUID> resolve(String audience, String value) {
        return switch (audience) {
            case "all" -> audiences.allActive();
            case "wing" -> audiences.activeInWing(value);
            case "flat" -> audiences.activeInFlat(flatFor(value).getId());
            case "committee" -> audiences.activeWithRoles(COMMITTEE);
            case "security" -> audiences.activeWithRoles(EnumSet.of(Role.SECURITY));
            case "user" -> {
                try {
                    yield audiences.activeById(UUID.fromString(value));
                } catch (IllegalArgumentException e) {
                    throw ApiException.badRequest("Unknown account.");
                }
            }
            default -> throw ApiException.badRequest("Unknown audience.");
        };
    }

    /** "A-101" → the active flat A-101. */
    private Flat flatFor(String label) {
        int dash = label.indexOf('-');
        if (dash < 1 || dash == label.length() - 1) {
            throw ApiException.badRequest("Write the flat as wing-number, for example A-101.");
        }
        String wing = label.substring(0, dash);
        String number = label.substring(dash + 1);
        return flats.findByWingAndFlatNumber(wing, number)
                .filter(Flat::isActive)
                .orElseThrow(() -> ApiException.badRequest("There is no flat " + label + "."));
    }

    private void forgetDead(ExpoPushClient.Result result) {
        if (!result.deadTokens().isEmpty()) {
            devices.deleteByTokenIn(result.deadTokens());
        }
    }

    private static Map<String, Object> data(Notification n) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notificationId", n.getId().toString());
        if (n.getScreen() != null) {
            data.put("screen", n.getScreen());
        }
        return data;
    }

    private static String[] text(String language, String flat, String amount, int count) {
        String[] t = DUES.getOrDefault(language, DUES.get("en"));
        return new String[] {t[0], String.format(t[1], flat, amount, count, count == 1 ? "bill" : "bills")};
    }

    /** 176642 → "1,76,642". */
    static String indian(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        f.setMaximumFractionDigits(2);
        return f.format(amount);
    }

    private static String json(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> params(Notification n) {
        if (n.getParams() == null) {
            return null;
        }
        try {
            return JSON.readValue(n.getParams(), new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
