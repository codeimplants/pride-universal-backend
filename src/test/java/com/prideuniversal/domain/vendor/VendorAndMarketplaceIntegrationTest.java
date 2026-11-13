package com.prideuniversal.domain.vendor;

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

class VendorAndMarketplaceIntegrationTest extends AbstractIntegrationTest {

    // --- Vendors ------------------------------------------------------------

    @Test
    @DisplayName("a resident's recommendation starts unverified; the committee verifies")
    void onlyCommitteeVerifies() throws Exception {
        Map<String, Object> v = vendor("Raju Plumbing");
        v.put("approved", true);
        JsonNode created = body(postAs(loginAsNewResident(), "/api/vendors", v)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.added_by_flat").exists()));

        v.put("version", created.get("version").asLong());
        putAs(loginAs(Role.SECRETARY), "/api/vendors/" + created.get("id").asText(), v)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    @DisplayName("only the committee or whoever added a vendor can change or remove it")
    void editRights() throws Exception {
        String adder = loginAsNewResident();
        JsonNode created = body(postAs(adder, "/api/vendors", vendor("Sharma Electricals")));
        String url = "/api/vendors/" + created.get("id").asText();
        Map<String, Object> edit = vendor("Sharma Electricals & Sons");
        edit.put("version", created.get("version").asLong());

        putAs(loginAsNewResident(), url, edit).andExpect(status().isForbidden());
        deleteAs(loginAsNewResident(), url).andExpect(status().isForbidden());

        deleteAs(adder, url).andExpect(status().isNoContent());
        assertThat(body(getAs(adder, "/api/vendors?service_type=electrician")).toString())
                .doesNotContain(created.get("id").asText());
    }

    // --- Marketplace --------------------------------------------------------

    @Test
    @DisplayName("a giveaway is free whatever price is sent; contact defaults to the poster")
    void giveawayIsFree() throws Exception {
        String mobile = newResidentMobile();
        Map<String, Object> l = listing("Old sofa");
        l.put("listing_type", "give_away");
        l.put("price", 5000);

        postAs(loginAs(mobile), "/api/marketplace", l)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.is_free").value(true))
                .andExpect(jsonPath("$.price").value(0))
                .andExpect(jsonPath("$.contact_number").value(mobile));
    }

    @Test
    @DisplayName("only the poster marks a listing sold; sold leaves the board but stays in 'mine'")
    void markSold() throws Exception {
        String poster = loginAsNewResident();
        JsonNode created = body(postAs(poster, "/api/marketplace", listing("Study table")));
        String id = created.get("id").asText();
        Map<String, Object> sold = listing("Study table");
        sold.put("status", "sold");
        sold.put("version", created.get("version").asLong());

        putAs(loginAsNewResident(), "/api/marketplace/" + id, sold).andExpect(status().isForbidden());
        putAs(poster, "/api/marketplace/" + id, sold).andExpect(status().isOk());

        assertThat(body(getAs(poster, "/api/marketplace")).toString()).doesNotContain(id);
        assertThat(body(getAs(poster, "/api/marketplace?mine=true")).toString()).contains(id);
    }

    private static Map<String, Object> vendor(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("service_type", "electrician");
        body.put("contact", "9123456780");
        return body;
    }

    private static Map<String, Object> listing(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("category", "furniture");
        body.put("listing_type", "sell");
        body.put("price", 1500);
        return body;
    }
}
