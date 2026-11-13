package com.prideuniversal.domain.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdminIntegrationTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the secretary cannot manage accounts")
    void secretaryCannotManage() throws Exception {
        getAs(loginAs(Role.SECRETARY), "/api/users").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a chairman creates committee accounts but cannot mint an admin")
    void noGrantingAboveYourself() throws Exception {
        String chairman = loginAs(Role.CHAIRMAN);
        postAs(chairman, "/api/users", user(Role.ADMIN)).andExpect(status().isForbidden());
        postAs(chairman, "/api/users", user(Role.COMMITTEE_MEMBER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("committee_member"));
    }

    @Test
    @DisplayName("a chairman cannot edit an admin; an admin cannot demote themselves")
    void hierarchyHolds() throws Exception {
        String adminMobile = newUserMobile(Role.ADMIN);
        User admin = userRepository.findByMobile(adminMobile).orElseThrow();
        Map<String, Object> demote = new HashMap<>();
        demote.put("display_name", admin.getDisplayName());
        demote.put("role", "resident");
        demote.put("is_active", true);
        demote.put("version", admin.getVersion());

        putAs(loginAs(Role.CHAIRMAN), "/api/users/" + admin.getId(), demote).andExpect(status().isForbidden());

        demote.put("role", "chairman");
        putAs(loginAs(adminMobile), "/api/users/" + admin.getId(), demote).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a resident edits their name, then deletes their account; signing in again starts afresh")
    void profileAndDeletion() throws Exception {
        String mobile = newResidentMobile();
        String token = loginAs(mobile);

        putAs(token, "/api/auth/me", Map.of("display_name", "Asha K."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("Asha K."));

        String oldId = userRepository.findByMobile(mobile).orElseThrow().getId().toString();
        deleteAs(token, "/api/auth/me").andExpect(status().isNoContent());
        assertThat(userRepository.findByMobile(mobile)).isEmpty();

        // The resend cooldown counts every OTP sent to the number in the last
        // minute, used or not; clear it rather than wait.
        jdbcTemplate.update("delete from otp_request where mobile = ?", mobile);
        JsonNode me = body(getAs(loginAs(mobile), "/api/auth/me").andExpect(status().isOk()));
        assertThat(me.get("id").asText()).isNotEqualTo(oldId);
    }

    @Test
    @DisplayName("the dashboard gives residents their flat and the committee the society")
    void dashboard() throws Exception {
        getAs(loginAsNewResident(), "/api/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.my_flat.flat_number").exists())
                .andExpect(jsonPath("$.society").doesNotExist());
        getAs(loginAs(Role.ADMIN), "/api/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.society.active_flats").exists());
    }

    private Map<String, Object> user(Role role) {
        Map<String, Object> body = new HashMap<>();
        body.put("mobile", mobileFor(nextSeq()));
        body.put("display_name", "New " + role.getCode());
        body.put("role", role.getCode());
        return body;
    }
}
