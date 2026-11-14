package com.prideuniversal.domain.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> listing(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("category", "home_food");
        body.put("description", "Fresh lunch tiffin, delivered to your door by 1pm");
        body.put("offer", "10% off the first month for Pride Universal members");
        body.put("contact", "9876501234");
        body.put("link", "instagram.com/tiffin");
        body.put("delivers", true);
        return body;
    }

    private String create(String token, Map<String, Object> body) throws Exception {
        return body(postAs(token, "/api/businesses", body).andExpect(status().isCreated())).get("id").asText();
    }

    private static boolean listed(JsonNode list, String id) {
        return list.findValuesAsText("id").contains(id);
    }

    @Test
    @DisplayName("a member's listing waits for the committee, then everyone sees it")
    void reviewFlow() throws Exception {
        String owner = loginAsNewResident();
        String neighbour = loginAsNewResident();
        String name = "Tiffin " + nextSeq();

        JsonNode created = body(postAs(owner, "/api/businesses", listing(name))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.is_mine").value(true))
                .andExpect(jsonPath("$.offer_active").value(true))
                .andExpect(jsonPath("$.link").value("https://instagram.com/tiffin")));
        String id = created.get("id").asText();
        assertThat(created.get("flat_number").asText()).isNotBlank();

        assertThat(listed(body(getAs(neighbour, "/api/businesses")), id)).isFalse();
        getAs(neighbour, "/api/businesses/" + id).andExpect(status().isNotFound());
        assertThat(listed(body(getAs(owner, "/api/businesses")), id)).isTrue();

        String committee = loginAs(Role.COMMITTEE_MEMBER);
        postAs(committee, "/api/businesses/" + id + "/review", Map.of("status", "approved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));

        getAs(neighbour, "/api/businesses/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_mine").value(false))
                .andExpect(jsonPath("$.name").value(name));
        assertThat(listed(body(getAs(neighbour, "/api/businesses?category=home_food")), id)).isTrue();
        assertThat(listed(body(getAs(neighbour, "/api/businesses?category=legal")), id)).isFalse();

        // Not theirs to change or to review.
        Map<String, Object> edit = listing(name + " (edited)");
        edit.put("version", created.get("version").asLong());
        putAs(neighbour, "/api/businesses/" + id, edit).andExpect(status().isForbidden());
        postAs(neighbour, "/api/businesses/" + id + "/review", Map.of("status", "hidden")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("fixing a rejected listing sends it back for review, and the owner sees why it was rejected")
    void rejectedThenResubmitted() throws Exception {
        String owner = loginAsNewResident();
        String id = create(owner, listing("Boutique " + nextSeq()));
        postAs(loginAs(Role.SECRETARY), "/api/businesses/" + id + "/review",
                Map.of("status", "rejected", "note", "Please add your shop hours"))
                .andExpect(status().isOk());

        JsonNode rejected = body(getAs(owner, "/api/businesses/" + id)
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.review_note").value("Please add your shop hours")));

        Map<String, Object> fixed = listing("Boutique, open 11 to 8");
        fixed.put("hours", "11am to 8pm");
        fixed.put("version", rejected.get("version").asLong());
        putAs(owner, "/api/businesses/" + id, fixed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    @DisplayName("the committee's own listings go up approved; expired offers stop showing; guards see nothing")
    void committeeOffersAndGuards() throws Exception {
        Map<String, Object> body = listing("CA practice " + nextSeq());
        body.put("category", "finance_insurance");
        body.put("offer_until", LocalDate.now().minusDays(2).toString());
        postAs(loginAs(Role.TREASURER), "/api/businesses", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.offer_active").value(false));

        getAs(loginAs(Role.SECURITY), "/api/businesses").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a member lists up to three businesses, with valid numbers")
    void limitsAndValidation() throws Exception {
        String owner = loginAsNewResident();
        for (int i = 0; i < BusinessService.MAX_PER_MEMBER; i++) {
            create(owner, listing("Shop " + i + " " + nextSeq()));
        }
        postAs(owner, "/api/businesses", listing("One too many")).andExpect(status().isConflict());

        Map<String, Object> bad = listing("Bad number");
        bad.put("contact", "12345");
        postAs(loginAsNewResident(), "/api/businesses", bad).andExpect(status().isBadRequest());
    }
}
