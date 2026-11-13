package com.prideuniversal.domain.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerAndSettingsIntegrationTest extends AbstractIntegrationTest {

    // --- Ledger -------------------------------------------------------------

    @Test
    @DisplayName("the books are the finance roles' only")
    void whoSeesTheBooks() throws Exception {
        getAs(loginAsNewResident(), "/api/transactions").andExpect(status().isForbidden());
        getAs(loginAs(Role.COMMITTEE_MEMBER), "/api/transactions").andExpect(status().isForbidden());
        postAs(loginAsNewResident(), "/api/transactions", entry("donation", 100, 2099, 1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("type follows the category, and totals cover the period")
    void totals() throws Exception {
        String treasurer = loginAs(Role.TREASURER);
        int month = 1 + nextSeq() % 12;
        String period = "/api/transactions?wing=common&year=2098&month=" + month;
        JsonNode before = body(getAs(treasurer, period));

        postAs(treasurer, "/api/transactions", entry("donation", 1000, 2098, month))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("income"));
        postAs(treasurer, "/api/transactions", entry("cleaning_expense", 400, 2098, month))
                .andExpect(jsonPath("$.type").value("expense"));

        JsonNode after = body(getAs(treasurer, period).andExpect(status().isOk()));
        org.assertj.core.api.Assertions.assertThat(after.get("income").decimalValue()
                        .subtract(before.get("income").decimalValue()))
                .isEqualByComparingTo("1000");
        org.assertj.core.api.Assertions.assertThat(after.get("balance").decimalValue()
                        .subtract(before.get("balance").decimalValue()))
                .isEqualByComparingTo("600");
    }

    // --- Settings -----------------------------------------------------------

    @Test
    @DisplayName("everyone reads the settings; only admin and chairman change them")
    void settings() throws Exception {
        String resident = loginAsNewResident();
        JsonNode wingA = body(getAs(resident, "/api/settings/A").andExpect(status().isOk()));

        ObjectNode edit = wingA.deepCopy();
        edit.put("society_name", "Pride Universal CHS");
        putAs(resident, "/api/settings/A", edit).andExpect(status().isForbidden());
        putAs(loginAs(Role.SECRETARY), "/api/settings/A", edit).andExpect(status().isForbidden());

        // Same amounts written back, so billing tests are unaffected.
        putAs(loginAs(Role.CHAIRMAN), "/api/settings/A", edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.society_name").value("Pride Universal CHS"))
                .andExpect(jsonPath("$.version").value(wingA.get("version").asLong() + 1));

        putAs(loginAs(Role.CHAIRMAN), "/api/settings/A", edit).andExpect(status().isConflict());
    }

    private static Map<String, Object> entry(String category, int amount, int year, int month) {
        Map<String, Object> body = new HashMap<>();
        body.put("wing", "common");
        body.put("category", category);
        body.put("amount", amount);
        body.put("txn_date", year + "-" + String.format("%02d", month) + "-05");
        return body;
    }
}
