package com.prideuniversal.domain.complaint;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComplaintApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("a resident's complaint is about their own flat, whatever the body says")
    void residentRaisesForOwnFlat() throws Exception {
        Flat own = createFlat("B", String.valueOf(nextSeq()), mobileFor(nextSeq()));
        String token = loginAs(own.getOwnerContact());

        Map<String, Object> body = complaint("Leaking tap");
        body.put("wing", "A");
        body.put("flat_number", "9999");

        postAs(token, "/api/complaints", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wing").value("B"))
                .andExpect(jsonPath("$.flat_number").value(own.getFlatNumber()))
                .andExpect(jsonPath("$.contact").value(own.getOwnerContact()))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.complaint_number").value(org.hamcrest.Matchers.startsWith("CMP-")));
    }

    @Test
    @DisplayName("a resident never sees another flat's complaint")
    void residentCannotSeeOthersComplaints() throws Exception {
        String other = loginAsNewResident();
        String id = body(postAs(other, "/api/complaints", complaint("Neighbour's ticket"))).get("id").asText();

        String resident = loginAsNewResident();
        assertThat(body(getAs(resident, "/api/complaints")).toString()).doesNotContain(id);
        getAs(resident, "/api/complaints/" + id).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("every committee role sees every complaint, filtered by a lowercase status")
    void committeeSeesAll() throws Exception {
        String id = body(postAs(loginAsNewResident(), "/api/complaints", complaint("Lift stuck"))).get("id").asText();

        JsonNode open = body(getAs(loginAs(Role.TREASURER), "/api/complaints?status=open")
                .andExpect(status().isOk()));
        assertThat(open.toString()).contains(id);
    }

    @Test
    @DisplayName("a resident cannot update a complaint, even their own")
    void residentCannotUpdate() throws Exception {
        String token = loginAsNewResident();
        JsonNode c = body(postAs(token, "/api/complaints", complaint("Mine")));

        putAs(token, "/api/complaints/" + c.get("id").asText(),
                Map.of("status", "closed", "version", c.get("version").asLong()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resolving stamps the resolved date; reopening clears it")
    void resolveAndReopen() throws Exception {
        JsonNode c = body(postAs(loginAsNewResident(), "/api/complaints", complaint("No water")));
        String url = "/api/complaints/" + c.get("id").asText();
        String helpdesk = loginAs(Role.COMMITTEE_MEMBER);

        JsonNode resolved = body(putAs(helpdesk, url, Map.of("status", "resolved", "assigned_to", "Plumber Raju",
                "version", c.get("version").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved_date").exists()));

        putAs(helpdesk, url, Map.of("status", "open", "version", resolved.get("version").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved_date").doesNotExist());
    }

    @Test
    @DisplayName("staff with no flat must name one; a named flat works")
    void staffRaiseOnBehalf() throws Exception {
        String guard = loginAs(Role.SECURITY);
        postAs(guard, "/api/complaints", complaint("Gate light")).andExpect(status().isBadRequest());

        Flat flat = createFlat();
        Map<String, Object> body = complaint("Reported by phone");
        body.put("wing", "a");
        body.put("flat_number", flat.getFlatNumber());
        postAs(guard, "/api/complaints", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wing").value("A"))
                .andExpect(jsonPath("$.flat_id").value(flat.getId().toString()));
    }

    private static Map<String, Object> complaint(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("category", "plumbing");
        body.put("title", title);
        body.put("description", "Details: " + title);
        body.put("priority", "high");
        return body;
    }
}
