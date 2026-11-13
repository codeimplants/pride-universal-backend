package com.prideuniversal.domain.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API Nexus federates to: its service token only, Nexus's field names, deletions logged. */
class AdminConsoleIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("no token, a wrong token, or even an admin's JWT: 401")
    void onlyTheServiceTokenOpensIt() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        getAs("wrong-token", "/api/admin/users").andExpect(status().isUnauthorized());
        getAs(loginAs(Role.ADMIN), "/api/admin/users").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("users carry the id, name, phone and registration date Nexus maps")
    void usersInNexusShape() throws Exception {
        String mobile = newUserMobile(Role.SECRETARY);
        User secretary = userRepository.findByMobile(mobile).orElseThrow();

        JsonNode users = body(getAs(CONSOLE_TOKEN, "/api/admin/users").andExpect(status().isOk())).get("data");
        JsonNode row = null;
        for (JsonNode u : users) {
            if (u.get("id").asText().equals(secretary.getId().toString())) row = u;
        }
        assertThat(row).as("the secretary is listed").isNotNull();
        assertThat(row.get("phone").asText()).isEqualTo(mobile);
        assertThat(row.get("name").asText()).isEqualTo(secretary.getDisplayName());
        // camelCase, as Nexus's adapter reads it - not this API's usual snake_case.
        assertThat(row.has("registrationDate")).isTrue();
    }

    @Test
    @DisplayName("deleting from Nexus removes the account and logs its id; a second delete is a 404")
    void deleteAndLog() throws Exception {
        String mobile = newResidentMobile();
        loginAs(mobile);
        UUID id = userRepository.findByMobile(mobile).orElseThrow().getId();

        deleteAs(CONSOLE_TOKEN, "/api/admin/users/" + id).andExpect(status().isNoContent());
        assertThat(userRepository.findById(id)).isEmpty();
        deleteAs(CONSOLE_TOKEN, "/api/admin/users/" + id).andExpect(status().isNotFound());

        JsonNode deleted = body(getAs(CONSOLE_TOKEN, "/api/admin/users/deleted")).get("data");
        boolean logged = false;
        for (JsonNode d : deleted) {
            logged |= id.toString().equals(d.path("originalUserId").asText());
        }
        assertThat(logged).as("logged under originalUserId, the key Nexus reads").isTrue();
    }

    @Test
    @DisplayName("stats: totals and one row per day")
    void stats() throws Exception {
        getAs(CONSOLE_TOKEN, "/api/admin/stats?days=7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.flats").exists())
                .andExpect(jsonPath("$.data.daily.length()").value(7));
    }
}
