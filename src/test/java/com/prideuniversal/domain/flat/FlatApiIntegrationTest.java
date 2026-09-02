package com.prideuniversal.domain.flat;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reference CRUD slice's tests: permissions, the PII boundary, optimistic
 * locking and soft delete.
 */
class FlatApiIntegrationTest extends AbstractIntegrationTest {

    // =====================================================================
    // The PII boundary - the reason the rewrite exists at all
    // =====================================================================

    @Test
    @DisplayName("the directory never carries contact numbers, whoever asks")
    void directoryHidesContactDetails() throws Exception {
        Flat flat = createFlat();
        String secret = flat.getOwnerContact();
        String token = loginAs(Role.ADMIN);

        String body = mockMvc.perform(get("/api/flats").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Even an admin gets the summary shape from the list endpoint: the
        // contact number is not merely hidden, it is not in the type.
        assertThat(body)
                .as("owner mobile must not appear anywhere in the directory payload")
                .doesNotContain(secret);
        assertThat(body).doesNotContain("owner_contact");
    }

    @Test
    @DisplayName("a resident cannot read another flat's full record")
    void residentCannotSeeAnotherFlatsDetail() throws Exception {
        Flat other = createFlat();
        String token = loginAsNewResident();

        mockMvc.perform(get("/api/flats/" + other.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("a resident can read their own flat in full")
    void residentCanSeeOwnFlatDetail() throws Exception {
        Flat own = createFlat();
        String token = loginAs(own.getOwnerContact());

        mockMvc.perform(get("/api/flats/" + own.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_contact").value(own.getOwnerContact()))
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    @DisplayName("committee members can read any flat in full")
    void committeeCanSeeAnyFlatDetail() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.SECRETARY);

        mockMvc.perform(get("/api/flats/" + flat.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_contact").value(flat.getOwnerContact()));
    }

    @Test
    @DisplayName("an unknown flat id is 404")
    void unknownFlatIsNotFound() throws Exception {
        String token = loginAs(Role.ADMIN);

        mockMvc.perform(get("/api/flats/" + UUID.randomUUID())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // Write permissions
    // =====================================================================

    @Test
    @DisplayName("a resident cannot add a flat")
    void residentCannotCreateFlat() throws Exception {
        String token = loginAsNewResident();

        mockMvc.perform(post("/api/flats")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flatJson("A", String.valueOf(nextSeq()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("a secretary can add a flat -> 201 with the new record")
    void secretaryCanCreateFlat() throws Exception {
        String token = loginAs(Role.SECRETARY);
        String number = String.valueOf(nextSeq());

        mockMvc.perform(post("/api/flats")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flatJson("A", number)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.flat_number").value(number))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("wing is normalised, so a lowercase wing still collides -> 409")
    void duplicateFlatNumberIsConflict() throws Exception {
        String token = loginAs(Role.SECRETARY);
        String number = String.valueOf(nextSeq());

        mockMvc.perform(post("/api/flats")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flatJson("A", number)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/flats")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flatJson("a", number)))
                .andExpect(status().isConflict());
    }

    // =====================================================================
    // Validation
    // =====================================================================

    @Test
    @DisplayName("a tenanted flat with no tenant name -> 400, not a database 409")
    void tenantWithoutNameIsBadRequest() throws Exception {
        String token = loginAs(Role.SECRETARY);

        Map<String, Object> body = flatMap("A", String.valueOf(nextSeq()));
        body.put("is_tenant", true);

        mockMvc.perform(post("/api/flats")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a malformed mobile is reported per-field")
    void invalidMobileIsAFieldError() throws Exception {
        String token = loginAs(Role.SECRETARY);

        Map<String, Object> body = flatMap("A", String.valueOf(nextSeq()));
        body.put("owner_contact", "12345");

        mockMvc.perform(post("/api/flats")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.field_errors.ownerContact").exists());
    }

    // =====================================================================
    // Optimistic locking
    // =====================================================================

    @Test
    @DisplayName("an update with the current version succeeds and bumps it")
    void updateWithCurrentVersionSucceeds() throws Exception {
        String token = loginAs(Role.SECRETARY);
        JsonNode created = createViaApi(token);

        Map<String, Object> update = flatMap(created.get("wing").asText(),
                created.get("flat_number").asText());
        update.put("owner_name", "Renamed Owner");
        update.put("version", created.get("version").asLong());

        String firstResponse = mockMvc.perform(put("/api/flats/" + created.get("id").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_name").value("Renamed Owner"))
                .andExpect(jsonPath("$.version").value(created.get("version").asLong() + 1))
                .andReturn().getResponse().getContentAsString();

        // The property that actually matters to a client: the version handed
        // back must be usable for the next write. When the response echoed a
        // stale version, this second update failed with a 409 the client could
        // never recover from without re-fetching.
        Map<String, Object> again = flatMap(created.get("wing").asText(),
                created.get("flat_number").asText());
        again.put("owner_name", "Renamed Twice");
        again.put("version", objectMapper.readTree(firstResponse).get("version").asLong());

        mockMvc.perform(put("/api/flats/" + created.get("id").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(again)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_name").value("Renamed Twice"));
    }

    @Test
    @DisplayName("an update with a stale version -> 409, and does not overwrite")
    void updateWithStaleVersionIsConflict() throws Exception {
        String token = loginAs(Role.SECRETARY);
        JsonNode created = createViaApi(token);
        String id = created.get("id").asText();
        long staleVersion = created.get("version").asLong();

        // First writer wins.
        Map<String, Object> first = flatMap(created.get("wing").asText(),
                created.get("flat_number").asText());
        first.put("owner_name", "First Writer");
        first.put("version", staleVersion);
        mockMvc.perform(put("/api/flats/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        // Second writer still holds the version it read before the first save.
        Map<String, Object> second = flatMap(created.get("wing").asText(),
                created.get("flat_number").asText());
        second.put("owner_name", "Second Writer");
        second.put("version", staleVersion);
        mockMvc.perform(put("/api/flats/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("concurrent_modification"));

        // The losing write must not have landed.
        assertThat(flatRepository.findById(UUID.fromString(id)).orElseThrow().getOwnerName())
                .isEqualTo("First Writer");
    }

    @Test
    @DisplayName("an update with no version at all -> 400, never a silent overwrite")
    void updateWithoutVersionIsBadRequest() throws Exception {
        String token = loginAs(Role.SECRETARY);
        JsonNode created = createViaApi(token);

        Map<String, Object> update = flatMap(created.get("wing").asText(),
                created.get("flat_number").asText());
        update.put("owner_name", "No Version");

        mockMvc.perform(put("/api/flats/" + created.get("id").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // Soft delete
    // =====================================================================

    @Test
    @DisplayName("a secretary may edit but not deactivate")
    void secretaryCannotDeactivate() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.SECRETARY);

        mockMvc.perform(delete("/api/flats/" + flat.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin deactivates: 204, row kept, gone from the directory")
    void adminCanDeactivate() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.ADMIN);

        mockMvc.perform(delete("/api/flats/" + flat.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        // Soft delete: the row survives, because bills and complaints point at it.
        assertThat(flatRepository.findById(flat.getId()).orElseThrow().isActive()).isFalse();

        String directory = mockMvc.perform(get("/api/flats")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        assertThat(directory).doesNotContain(flat.getId().toString());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private JsonNode createViaApi(String token) throws Exception {
        String body = mockMvc.perform(post("/api/flats")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flatJson("A", String.valueOf(nextSeq()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String flatJson(String wing, String number) throws Exception {
        return objectMapper.writeValueAsString(flatMap(wing, number));
    }

    /**
     * Keys are written in snake_case on purpose: Jackson's naming strategy
     * renames POJO properties, not Map keys, so a Map has to be spelled the
     * way the wire is spelled.
     */
    private Map<String, Object> flatMap(String wing, String number) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wing", wing);
        body.put("flat_number", number);
        body.put("floor", 1);
        body.put("owner_name", "Api Owner " + number);
        return body;
    }
}
