package com.prideuniversal.domain.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Push itself is off in tests (app.push.enabled=false): these check who is reached and the inbox. */
class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static Map<String, Object> device(String token, String language) {
        return Map.of("token", token, "platform", "android", "device_name", "Test phone", "language", language);
    }

    private static Map<String, Object> message(String audience, String value) {
        return value == null
                ? Map.of("title", "Water cut", "body", "No water 10am to 2pm on Sunday.", "audience", audience)
                : Map.of("title", "Water cut", "body", "No water 10am to 2pm on Sunday.", "audience", audience,
                        "audience_value", value, "screen", "notices");
    }

    private static JsonNode first(JsonNode inbox, String kind) {
        for (JsonNode n : inbox) {
            if (kind.equals(n.get("kind").asText())) {
                return n;
            }
        }
        return null;
    }

    @Test
    @DisplayName("the committee writes to one flat: it lands in that flat's inbox, on its phones, and nowhere else")
    void sendToFlat() throws Exception {
        Flat flat = createFlat();
        String resident = loginAs(flat.getOwnerContact());
        String neighbour = loginAsNewResident();
        postAs(resident, "/api/push/devices", device("ExponentPushToken[flat-" + nextSeq() + "]", "mr"))
                .andExpect(status().isNoContent());

        postAs(loginAs(Role.SECRETARY), "/api/notifications", message("flat", "a-" + flat.getFlatNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients").value(1))
                .andExpect(jsonPath("$.devices").value(1))
                .andExpect(jsonPath("$.delivered").value(0));   // push is off in tests

        JsonNode inbox = body(getAs(resident, "/api/notifications").andExpect(status().isOk()));
        JsonNode note = first(inbox, "message");
        assertThat(note).isNotNull();
        assertThat(note.get("title").asText()).isEqualTo("Water cut");
        assertThat(note.get("screen").asText()).isEqualTo("notices");
        assertThat(note.get("read").asBoolean()).isFalse();
        assertThat(first(body(getAs(neighbour, "/api/notifications")), "message")).isNull();

        postAs(resident, "/api/notifications/read", Map.of("ids", List.of(note.get("id").asText())))
                .andExpect(jsonPath("$.updated").value(1));
        assertThat(first(body(getAs(resident, "/api/notifications")), "message").get("read").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("residents and treasurers do not broadcast; a flat nobody uses is refused, not silently empty")
    void whoMaySend() throws Exception {
        postAs(loginAsNewResident(), "/api/notifications", message("all", null)).andExpect(status().isForbidden());
        postAs(loginAs(Role.TREASURER), "/api/notifications", message("all", null)).andExpect(status().isForbidden());

        Flat empty = createFlat();   // nobody has signed in for it
        postAs(loginAs(Role.CHAIRMAN), "/api/notifications", message("flat", "A-" + empty.getFlatNumber()))
                .andExpect(status().isBadRequest());
        postAs(loginAs(Role.CHAIRMAN), "/api/notifications", message("flat", "Z-0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a phone moves to whoever signs in on it, and only its owner removes it")
    void deviceOwnership() throws Exception {
        String token = "ExponentPushToken[shared-" + nextSeq() + "]";
        String first = loginAsNewResident();
        String second = loginAsNewResident();
        postAs(first, "/api/push/devices", device(token, "en")).andExpect(status().isNoContent());
        postAs(second, "/api/push/devices", device(token, "hi")).andExpect(status().isNoContent());

        deleteAs(first, "/api/push/devices?token=" + token).andExpect(status().isNoContent());
        Integer stillThere = jdbcTemplate.queryForObject("select count(*) from push_device where token = ?", Integer.class, token);
        assertThat(stillThere).isEqualTo(1);
        deleteAs(second, "/api/push/devices?token=" + token).andExpect(status().isNoContent());
        Integer gone = jdbcTemplate.queryForObject("select count(*) from push_device where token = ?", Integer.class, token);
        assertThat(gone).isZero();

        postAs(first, "/api/push/devices", device("not-a-token", "en")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("dues reminders tell each flat with unpaid bills how much it owes")
    void duesReminders() throws Exception {
        Flat flat = createFlat();
        String resident = loginAs(flat.getOwnerContact());
        jdbcTemplate.update("""
                insert into maintenance_bill (flat_id, wing, flat_number, owner_name, month, year, amount, late_fee, status, due_date)
                values (?, ?, ?, ?, 1, 2094, 2750, 100, 'overdue', date '2094-01-10')
                """, flat.getId(), flat.getWing(), flat.getFlatNumber(), flat.getOwnerName());

        postAs(loginAsNewResident(), "/api/notifications/dues-reminders", null).andExpect(status().isForbidden());
        JsonNode result = body(postAs(loginAs(Role.TREASURER), "/api/notifications/dues-reminders", null)
                .andExpect(status().isOk()));
        assertThat(result.get("flats").asInt()).isPositive();

        JsonNode reminder = first(body(getAs(resident, "/api/notifications")), "dues_reminder");
        assertThat(reminder).isNotNull();
        assertThat(reminder.get("screen").asText()).isEqualTo("my-maintenance");
        assertThat(reminder.get("params").get("flat").asText()).isEqualTo("A-" + flat.getFlatNumber());
        assertThat(reminder.get("params").get("amount").asText()).isEqualTo("2,850");
        assertThat(reminder.get("params").get("bills").asInt()).isEqualTo(1);

        getAs(loginAs(Role.TREASURER), "/api/notifications/sent").andExpect(status().isOk());
    }

}
