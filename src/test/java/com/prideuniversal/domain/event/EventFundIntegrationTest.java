package com.prideuniversal.domain.event;

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

class EventFundIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> event(String name, String start, Object carriedFrom, Object opening, boolean published) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("start_date", start);
        body.put("carried_from_id", carriedFrom);
        body.put("opening_balance", opening);
        body.put("is_published", published);
        return body;
    }

    private static Map<String, Object> entry(String type, String head, int amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("head", head);
        body.put("amount", amount);
        return body;
    }

    private static void assertMoney(JsonNode node, String field, String expected) {
        assertThat(node.get(field).decimalValue()).as(field).isEqualByComparingTo(expected);
    }

    private String create(String token, Map<String, Object> body) throws Exception {
        return body(postAs(token, "/api/events", body).andExpect(status().isCreated())).get("id").asText();
    }

    @Test
    @DisplayName("the poster's numbers: Ganesh Festival closes at 77,114 and Kojagiri, spending it, at 34,713")
    void ganeshThenKojagiri() throws Exception {
        String committee = loginAs(Role.COMMITTEE_MEMBER);
        int n = nextSeq();

        String ganesh = create(committee, event("Ganesh Festival " + n, "2090-09-14", null, 7680, true));
        postAs(committee, "/api/events/" + ganesh + "/entries", entry("income", "vargani", 176642))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("income"));
        for (int[] spent : new int[][] {{8920}, {12712}, {7558}, {57491}, {8802}, {10905}, {820}}) {
            postAs(committee, "/api/events/" + ganesh + "/entries", entry("expense", "head", spent[0]))
                    .andExpect(status().isCreated());
        }

        JsonNode g = body(getAs(committee, "/api/events/" + ganesh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(8)));
        assertMoney(g, "opening_balance", "7680");
        assertMoney(g, "income", "176642");
        assertMoney(g, "expense", "107208");
        assertMoney(g, "closing_balance", "77114");

        String kojagiri = create(committee, event("Kojagiri " + n, "2090-10-25", ganesh, 999, true));
        for (int spent : new int[] {6240, 28000, 4474, 1980, 385, 1322}) {
            postAs(committee, "/api/events/" + kojagiri + "/entries", entry("expense", "Kojagiri item", spent))
                    .andExpect(status().isCreated());
        }

        JsonNode k = body(getAs(loginAsNewResident(), "/api/events/" + kojagiri)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carried_from.name").value("Ganesh Festival " + n)));
        assertMoney(k, "opening_balance", "77114");      // carried, not the 999 typed
        assertMoney(k, "expense", "42401");
        assertMoney(k, "closing_balance", "34713");

        getAs(committee, "/api/events/" + ganesh)
                .andExpect(jsonPath("$.carried_to.name").value("Kojagiri " + n));

        // A correction to the Ganesh books flows through to Kojagiri's opening balance.
        postAs(committee, "/api/events/" + ganesh + "/entries", entry("income", "donation", 1000))
                .andExpect(status().isCreated());
        assertMoney(body(getAs(committee, "/api/events/" + kojagiri)), "closing_balance", "35713");
    }

    @Test
    @DisplayName("drafts are the committee's; members read published events; guards read neither")
    void visibility() throws Exception {
        String committee = loginAs(Role.TREASURER);
        String draft = create(committee, event("Draft " + nextSeq(), "2091-01-01", null, 0, false));
        String resident = loginAsNewResident();

        getAs(resident, "/api/events/" + draft).andExpect(status().isNotFound());
        JsonNode list = body(getAs(resident, "/api/events").andExpect(status().isOk()));
        assertThat(list.findValuesAsText("id")).doesNotContain(draft);

        getAs(committee, "/api/events/" + draft).andExpect(status().isOk());
        getAs(loginAs(Role.SECURITY), "/api/events").andExpect(status().isForbidden());

        postAs(resident, "/api/events", event("Mine", "2091-01-01", null, 0, true)).andExpect(status().isForbidden());
        postAs(resident, "/api/events/" + draft + "/entries", entry("expense", "x", 1)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a balance is carried forward once, from an earlier event, and pins its source")
    void carryRules() throws Exception {
        String committee = loginAs(Role.SECRETARY);
        int n = nextSeq();
        String first = create(committee, event("First " + n, "2092-01-01", null, 100, true));
        create(committee, event("Second " + n, "2092-02-01", first, null, true));

        postAs(committee, "/api/events", event("Third " + n, "2092-03-01", first, null, true))
                .andExpect(status().isConflict());
        postAs(committee, "/api/events", event("Earlier " + n, "2091-12-01", first, null, true))
                .andExpect(status().isBadRequest());
        deleteAs(committee, "/api/events/" + first).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("entries are corrected with their version and removed with the event")
    void entryLifecycle() throws Exception {
        String committee = loginAs(Role.CHAIRMAN);
        String id = create(committee, event("Navratri " + nextSeq(), "2093-10-01", null, 0, true));
        JsonNode e = body(postAs(committee, "/api/events/" + id + "/entries", entry("expense", "mandap_decoration", 500))
                .andExpect(status().isCreated()));

        Map<String, Object> fix = entry("expense", "mandap_decoration", 650);
        putAs(committee, "/api/events/" + id + "/entries/" + e.get("id").asText(), fix).andExpect(status().isBadRequest());
        fix.put("version", e.get("version").asLong());
        assertMoney(body(putAs(committee, "/api/events/" + id + "/entries/" + e.get("id").asText(), fix)
                .andExpect(status().isOk())), "amount", "650");
        assertMoney(body(getAs(committee, "/api/events/" + id)), "closing_balance", "-650");

        postAs(committee, "/api/events/" + id + "/entries", entry("expense", "x", 0)).andExpect(status().isBadRequest());
        deleteAs(committee, "/api/events/" + id).andExpect(status().isNoContent());
        getAs(committee, "/api/events/" + id).andExpect(status().isNotFound());
    }
}
