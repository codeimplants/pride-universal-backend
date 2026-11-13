package com.prideuniversal.domain.visitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VisitorApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("gate logs, the flat approves, the gate checks out")
    void fullVisit() throws Exception {
        Flat flat = createFlat();
        String guard = loginAs(Role.SECURITY);

        JsonNode v = body(postAs(guard, "/api/visitors", visit(flat, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.resident_name").value(flat.getOwnerName())));
        String url = "/api/visitors/" + v.get("id").asText();

        String resident = loginAs(flat.getOwnerContact());
        postAs(resident, url + "/approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.entry_time").exists());

        postAs(guard, url + "/check-out", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("checked_out"))
                .andExpect(jsonPath("$.exit_time").exists());
    }

    @Test
    @DisplayName("without awaiting approval the gate lets the visitor straight in")
    void directEntry() throws Exception {
        postAs(loginAs(Role.SECURITY), "/api/visitors", visit(createFlat(), false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"));
    }

    @Test
    @DisplayName("residents cannot log visitors, nor see or approve another flat's")
    void residentsAreConfinedToTheirFlat() throws Exception {
        String resident = loginAsNewResident();
        postAs(resident, "/api/visitors", visit(createFlat(), true)).andExpect(status().isForbidden());

        String id = body(postAs(loginAs(Role.SECURITY), "/api/visitors", visit(createFlat(), true))).get("id").asText();
        getAs(resident, "/api/visitors/" + id).andExpect(status().isNotFound());
        postAs(resident, "/api/visitors/" + id + "/approve", null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("out-of-order transitions are a 409")
    void transitionsAreGuarded() throws Exception {
        String guard = loginAs(Role.SECURITY);
        String url = "/api/visitors/" + body(postAs(guard, "/api/visitors", visit(createFlat(), true))).get("id").asText();

        postAs(guard, url + "/check-out", null).andExpect(status().isConflict());
        postAs(guard, url + "/reject", Map.of("reason", "Not expected")).andExpect(status().isOk());
        postAs(guard, url + "/approve", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an unknown flat is a 400")
    void unknownFlat() throws Exception {
        Map<String, Object> body = visit(createFlat(), true);
        body.put("flat_number", "NOPE");
        postAs(loginAs(Role.SECURITY), "/api/visitors", body).andExpect(status().isBadRequest());
    }

    private static Map<String, Object> visit(Flat flat, boolean awaitApproval) {
        Map<String, Object> body = new HashMap<>();
        body.put("visitor_name", "Courier");
        body.put("purpose", "delivery");
        body.put("wing", flat.getWing());
        body.put("flat_number", flat.getFlatNumber());
        body.put("vehicle_number", "mh12 ab 1234");
        body.put("await_approval", awaitApproval);
        return body;
    }
}
