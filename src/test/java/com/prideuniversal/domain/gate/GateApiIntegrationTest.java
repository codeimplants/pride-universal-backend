package com.prideuniversal.domain.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GateApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("a resident's single-use pass lets the guest in once, approved, and tells the flat")
    void singleUsePass() throws Exception {
        Flat flat = createFlat();
        String resident = loginAs(flat.getOwnerContact());
        JsonNode pass = body(postAs(resident, "/api/gate/passes", pass(false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("active"))
                .andExpect(jsonPath("$.wing").value(flat.getWing())));
        String code = pass.get("code").asText();
        assertThat(code).matches("^[0-9]{6}$");

        String guard = loginAs(Role.SECURITY);
        postAs(guard, "/api/gate/passes/use", Map.of("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.flat_number").value(flat.getFlatNumber()));
        postAs(guard, "/api/gate/passes/use", Map.of("code", code)).andExpect(status().isConflict());

        getAs(resident, "/api/notifications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("gate"))
                .andExpect(jsonPath("$[0].params.event").value("pass_used"));
    }

    @Test
    @DisplayName("a multi-use pass works repeatedly; a cancelled one does not")
    void multiUseAndCancel() throws Exception {
        String resident = loginAs(createFlat().getOwnerContact());
        String guard = loginAs(Role.SECURITY);
        JsonNode p = body(postAs(resident, "/api/gate/passes", pass(true)));
        String code = p.get("code").asText();
        postAs(guard, "/api/gate/passes/use", Map.of("code", code)).andExpect(status().isOk());
        postAs(guard, "/api/gate/passes/use", Map.of("code", code)).andExpect(status().isOk());

        postAs(resident, "/api/gate/passes/" + p.get("id").asText() + "/cancel", null)
                .andExpect(jsonPath("$.state").value("cancelled"));
        postAs(guard, "/api/gate/passes/use", Map.of("code", code)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("residents can't use passes at the gate or cancel another flat's")
    void passPermissions() throws Exception {
        String owner = loginAs(createFlat().getOwnerContact());
        JsonNode p = body(postAs(owner, "/api/gate/passes", pass(false)));
        String other = loginAs(createFlat().getOwnerContact());
        postAs(other, "/api/gate/passes/use", Map.of("code", p.get("code").asText())).andExpect(status().isForbidden());
        postAs(other, "/api/gate/passes/" + p.get("id").asText() + "/cancel", null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a pass can't start in the past")
    void pastPass() throws Exception {
        Map<String, Object> body = pass(false);
        body.put("valid_from", LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(2).toString());
        postAs(loginAs(createFlat().getOwnerContact()), "/api/gate/passes", body).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("daily help: added by the flat, in and out at the gate, attendance kept, the flat told")
    void dailyHelp() throws Exception {
        Flat flat = createFlat();
        String resident = loginAs(flat.getOwnerContact());
        JsonNode h = body(postAs(resident, "/api/gate/help",
                Map.of("name", "Sunita", "mobile", mobileFor(nextSeq()), "help_type", "maid"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flats[0]").value(flat.getWing() + "-" + flat.getFlatNumber())));
        String id = h.get("id").asText();

        String guard = loginAs(Role.SECURITY);
        postAs(guard, "/api/gate/help/check-in", Map.of("code", h.get("code").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inside").value(true));
        postAs(guard, "/api/gate/help/check-in", Map.of("code", h.get("code").asText()))
                .andExpect(status().isConflict());
        postAs(guard, "/api/gate/help/" + id + "/check-out", null)
                .andExpect(jsonPath("$.inside").value(false));

        getAs(resident, "/api/gate/help/" + id + "/attendance")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exit_time").exists());
        getAs(resident, "/api/notifications")
                .andExpect(jsonPath("$[0].params.event").value("help_out"));

        // Another flat neither sees them nor their attendance.
        String other = loginAs(createFlat().getOwnerContact());
        getAs(other, "/api/gate/help/" + id + "/attendance").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the same help added by two flats, by mobile, is one person serving both")
    void sharedHelp() throws Exception {
        String mobile = mobileFor(nextSeq());
        Map<String, Object> help = Map.of("name", "Ramesh", "mobile", mobile, "help_type", "driver");
        String a = body(postAs(loginAs(createFlat().getOwnerContact()), "/api/gate/help", help)).get("id").asText();
        JsonNode b = body(postAs(loginAs(createFlat().getOwnerContact()), "/api/gate/help", help));
        assertThat(b.get("id").asText()).isEqualTo(a);
        assertThat(b.get("flats").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("a visitor waiting at the gate alerts the flat, and the flat's answer alerts the guards")
    void visitorAlerts() throws Exception {
        Flat flat = createFlat();
        // A resident's account exists from their first sign-in; only then can they be alerted.
        String resident = loginAs(flat.getOwnerContact());
        String guard = loginAs(Role.SECURITY);
        Map<String, Object> v = new HashMap<>(Map.of("visitor_name", "Courier", "purpose", "delivery",
                "wing", flat.getWing(), "flat_number", flat.getFlatNumber(), "await_approval", true));
        String id = body(postAs(guard, "/api/visitors", v)).get("id").asText();

        getAs(resident, "/api/notifications").andExpect(jsonPath("$[0].params.event").value("waiting"));
        postAs(resident, "/api/visitors/" + id + "/approve", null).andExpect(status().isOk());
        getAs(guard, "/api/notifications").andExpect(jsonPath("$[0].params.event").value("approved"));
    }

    private static Map<String, Object> pass(boolean multiUse) {
        Map<String, Object> m = new HashMap<>();
        m.put("guest_name", "Anil");
        m.put("purpose", "guest");
        m.put("multi_use", multiUse);
        if (multiUse) {
            m.put("valid_until", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(7).toString());
        }
        return m;
    }
}
