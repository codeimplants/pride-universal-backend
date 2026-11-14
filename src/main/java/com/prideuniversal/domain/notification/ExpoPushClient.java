package com.prideuniversal.domain.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prideuniversal.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends through Expo's push service, which forwards to FCM (Android) and APNs (iOS), so one call
 * reaches both. The same service and shape as Nexus's {@code PushService}.
 *
 * <p>Expo answers each message with a ticket, in order. {@code DeviceNotRegistered} means the app was
 * uninstalled or the token rotated; the caller deletes those tokens instead of retrying them forever.
 *
 * <p>Keys are built as plain maps, not records: the app's ObjectMapper writes snake_case, and Expo
 * wants {@code channelId}.
 */
@Component
public class ExpoPushClient {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);
    private static final URI EXPO = URI.create("https://exp.host/--/api/v2/push/send");
    private static final int CHUNK = 100;   // Expo's per-request limit
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One phone's message; the text is already in that phone's language. */
    public record Message(String token, String title, String body, Map<String, Object> data) {}

    /** {@code ok} is per message, in the order sent, so a caller can attribute deliveries. */
    public record Result(List<Boolean> ok, List<String> deadTokens, List<String> errors) {
        static Result none(int messages) {
            return new Result(java.util.Collections.nCopies(messages, false), List.of(), List.of());
        }

        public int sent() {
            return (int) ok.stream().filter(Boolean::booleanValue).count();
        }
    }

    private final boolean enabled;
    private final String accessToken;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ExpoPushClient(AppProperties props) {
        AppProperties.Push push = props.push();
        this.enabled = push != null && push.enabled();
        this.accessToken = push == null ? null : push.expoAccessToken();
    }

    /** Never throws: a push that fails leaves the notification in the inbox, which is what matters. */
    public Result send(List<Message> messages) {
        if (messages.isEmpty() || !enabled) {
            if (!messages.isEmpty()) {
                log.info("Push disabled: {} message(s) recorded for the inbox only", messages.size());
            }
            return Result.none(messages.size());
        }
        List<Boolean> ok = new ArrayList<>(java.util.Collections.nCopies(messages.size(), false));
        List<String> dead = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += CHUNK) {
            List<Message> batch = messages.subList(i, Math.min(messages.size(), i + CHUNK));
            try {
                JsonNode tickets = post(batch).path("data");
                for (int j = 0; j < batch.size(); j++) {
                    JsonNode t = tickets.path(j);
                    if ("ok".equals(t.path("status").asText())) {
                        ok.set(i + j, true);
                    } else if ("DeviceNotRegistered".equals(t.path("details").path("error").asText())) {
                        dead.add(batch.get(j).token());
                    } else {
                        errors.add(t.path("message").asText(t.path("details").path("error").asText("unknown error")));
                    }
                }
            } catch (Exception e) {
                log.warn("Expo push failed for {} message(s): {}", batch.size(), e.getMessage());
                errors.add("Push service unreachable: " + e.getMessage());
            }
        }
        return new Result(ok, dead, errors);
    }

    private JsonNode post(List<Message> batch) throws Exception {
        List<Map<String, Object>> body = new ArrayList<>(batch.size());
        for (Message m : batch) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("to", m.token());
            one.put("title", m.title());
            one.put("body", m.body());
            one.put("data", m.data() == null ? Map.of() : m.data());
            one.put("sound", "default");
            one.put("priority", "high");
            one.put("channelId", "alerts");   // the Android channel the app creates
            body.add(one);
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(EXPO)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (accessToken != null && !accessToken.isBlank()) {
            req.header("Authorization", "Bearer " + accessToken);
        }
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + res.statusCode() + ": "
                    + res.body().substring(0, Math.min(200, res.body().length())));
        }
        return JSON.readTree(res.body());
    }
}
