package com.prideuniversal.domain.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prideuniversal.common.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the gate tells people: the flat that a visitor is waiting, has come in on a pass, or that their
 * daily help has arrived or left; the guards that a flat has answered.
 *
 * <p>Sent after the gate's own transaction commits, so a slow or failing push never holds up the guard
 * or undoes the log. Kept as kind {@code gate} with {@code params}; the app writes the text in the
 * reader's language, and the push in each phone's.
 */
@Service
public class GateAlerts {

    private static final Logger log = LoggerFactory.getLogger(GateAlerts.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** event → language → {title, body}; {name}, {flat} and {what} are filled in. */
    private static final Map<String, Map<String, String[]>> TEXT = Map.of(
            "waiting", Map.of(
                    "en", new String[] {"{name} is at the gate", "{what} for flat {flat}. Tap to let them in or turn them away."},
                    "mr", new String[] {"{name} गेटवर आहेत", "फ्लॅट {flat} साठी. आत सोडायचे की नाही, ते ठरवा."},
                    "hi", new String[] {"{name} गेट पर हैं", "फ्लैट {flat} के लिए। अंदर आने दें या मना करें।"}),
            "approved", Map.of(
                    "en", new String[] {"Let {name} in", "Flat {flat} approved the visitor."},
                    "mr", new String[] {"{name} यांना आत सोडा", "फ्लॅट {flat} ने परवानगी दिली."},
                    "hi", new String[] {"{name} को अंदर आने दें", "फ्लैट {flat} ने अनुमति दी।"}),
            "rejected", Map.of(
                    "en", new String[] {"Don't let {name} in", "Flat {flat} turned the visitor away."},
                    "mr", new String[] {"{name} यांना आत सोडू नका", "फ्लॅट {flat} ने नकार दिला."},
                    "hi", new String[] {"{name} को अंदर न आने दें", "फ्लैट {flat} ने मना किया।"}),
            "pass_used", Map.of(
                    "en", new String[] {"{name} has arrived", "Let in at the gate with your pass."},
                    "mr", new String[] {"{name} आले आहेत", "तुमच्या गेट पासने आत सोडले."},
                    "hi", new String[] {"{name} आ गए हैं", "आपके गेट पास से अंदर आने दिया गया।"}),
            "help_in", Map.of(
                    "en", new String[] {"{name} has arrived", "Your {what} checked in at the gate."},
                    "mr", new String[] {"{name} आले आहेत", "गेटवर नोंद झाली."},
                    "hi", new String[] {"{name} आ गए हैं", "गेट पर एंट्री हो गई।"}),
            "help_out", Map.of(
                    "en", new String[] {"{name} has left", "Your {what} checked out at the gate."},
                    "mr", new String[] {"{name} गेले", "गेटवर बाहेर जाण्याची नोंद झाली."},
                    "hi", new String[] {"{name} चले गए", "गेट पर एग्ज़िट दर्ज हो गई।"}));

    private final PushDeviceRepository devices;
    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;
    private final RecipientQueries audiences;
    private final ExpoPushClient push;
    private final TransactionTemplate tx;

    public GateAlerts(PushDeviceRepository devices, NotificationRepository notifications,
                      NotificationRecipientRepository recipients, RecipientQueries audiences,
                      ExpoPushClient push, PlatformTransactionManager transactionManager) {
        this.devices = devices;
        this.notifications = notifications;
        this.recipients = recipients;
        this.audiences = audiences;
        this.push = push;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Everyone signed in on these flats. */
    public void toFlats(List<UUID> flatIds, String event, String name, String flat, String what, String refId) {
        afterCommit(() -> {
            List<UUID> users = new ArrayList<>();
            for (UUID f : flatIds) {
                for (UUID u : audiences.activeInFlat(f)) {
                    if (!users.contains(u)) {
                        users.add(u);
                    }
                }
            }
            send(users, "flat", flat, event, name, flat, what, refId);
        });
    }

    /** Every active guard. */
    public void toGate(String event, String name, String flat, String what, String refId) {
        afterCommit(() -> send(audiences.activeWithRoles(EnumSet.of(Role.SECURITY)), "security", null,
                event, name, flat, what, refId));
    }

    // ---------------------------------------------------------------------

    private void afterCommit(Runnable work) {
        Runnable safe = () -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                log.warn("Gate alert not sent: {}", e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }

    private void send(List<UUID> users, String audience, String audienceValue,
                      String event, String name, String flat, String what, String refId) {
        if (users.isEmpty()) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("event", event);
        params.put("name", name);
        params.put("flat", flat);
        if (what != null) {
            params.put("what", what);
        }
        if (refId != null) {
            params.put("ref", refId);
        }

        record Prepared(UUID id, List<ExpoPushClient.Message> messages) {}
        Prepared p = tx.execute(status -> {
            String[] en = text(event, "en", name, flat, what);
            Notification n = new Notification();
            n.setKind(Notification.GATE);
            n.setTitle(cut(en[0], 120));
            n.setBody(cut(en[1], 1000));
            n.setParams(json(params));
            n.setAudience(audience);
            n.setAudienceValue(audienceValue);
            n.setScreen("visitors");
            n.setSentBy("Gate");
            n.setRecipients(users.size());
            notifications.saveAndFlush(n);
            recipients.saveAll(users.stream().map(u -> NotificationRecipient.of(n.getId(), u)).toList());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("notificationId", n.getId().toString());
            data.put("screen", "visitors");
            List<ExpoPushClient.Message> messages = devices.findAllByUserIdIn(users).stream().map(d -> {
                String[] t = text(event, d.getLanguage(), name, flat, what);
                return new ExpoPushClient.Message(d.getToken(), t[0], t[1], data);
            }).toList();
            return new Prepared(n.getId(), messages);
        });

        ExpoPushClient.Result result = push.send(p.messages());
        tx.executeWithoutResult(status -> {
            notifications.findById(p.id()).ifPresent(n -> {
                n.setDevices(p.messages().size());
                n.setDelivered(result.sent());
            });
            if (!result.deadTokens().isEmpty()) {
                devices.deleteByTokenIn(result.deadTokens());
            }
        });
    }

    static String[] text(String event, String language, String name, String flat, String what) {
        Map<String, String[]> byLang = TEXT.get(event);
        String[] t = byLang.getOrDefault(language, byLang.get("en"));
        return new String[] {fill(t[0], name, flat, what), fill(t[1], name, flat, what)};
    }

    private static String fill(String s, String name, String flat, String what) {
        String w = what == null ? "visitor" : what.replace('_', ' ');
        if (s.startsWith("{what}")) {
            s = Character.toUpperCase(w.charAt(0)) + w.substring(1) + s.substring("{what}".length());
        }
        return s.replace("{name}", name).replace("{flat}", flat == null ? "" : flat).replace("{what}", w);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String json(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
