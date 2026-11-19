package com.prideuniversal.domain.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2ApiIntegrationTest extends AbstractIntegrationTest {

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("parcel: the gate logs it, the flat is told and collects it once")
    void parcel() throws Exception {
        Flat flat = createFlat();
        String resident = loginAs(flat.getOwnerContact());
        String guard = loginAs(Role.SECURITY);
        String id = body(postAs(guard, "/api/gate/parcels",
                Map.of("wing", flat.getWing(), "flat_number", flat.getFlatNumber(), "carrier", "Amazon"))
                .andExpect(status().isCreated())).get("id").asText();
        getAs(resident, "/api/notifications").andExpect(jsonPath("$[0].params.event").value("parcel"));
        getAs(resident, "/api/gate/parcels?waiting=true").andExpect(jsonPath("$[0].id").value(id));
        postAs(resident, "/api/gate/parcels/" + id + "/collect", null).andExpect(jsonPath("$.collected_at").exists());
        postAs(guard, "/api/gate/parcels/" + id + "/collect", null).andExpect(status().isConflict());
        postAs(resident, "/api/gate/parcels", Map.of("wing", flat.getWing(), "flat_number", flat.getFlatNumber()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("blacklist: the committee bans a mobile, the gate can't log that visitor")
    void blacklist() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String mobile = mobileFor(nextSeq());
        postAs(admin, "/api/gate/blacklist", Map.of("name", "Trouble", "mobile", mobile, "reason", "Theft"))
                .andExpect(status().isCreated());
        Flat flat = createFlat();
        Map<String, Object> v = new HashMap<>(Map.of("visitor_name", "Someone", "visitor_contact", mobile,
                "purpose", "personal", "wing", flat.getWing(), "flat_number", flat.getFlatNumber(), "await_approval", false));
        postAs(loginAs(Role.SECURITY), "/api/visitors", v).andExpect(status().isConflict());
        postAs(loginAs(Role.SECURITY), "/api/gate/blacklist", Map.of("name", "X", "mobile", mobile))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("patrol: the committee adds a checkpoint, a guard scans its code")
    void patrol() throws Exception {
        JsonNode c = body(postAs(loginAs(Role.ADMIN), "/api/gate/patrol/checkpoints", Map.of("name", "Terrace"))
                .andExpect(status().isCreated()));
        String guard = loginAs(Role.SECURITY);
        postAs(guard, "/api/gate/patrol/scan", Map.of("code", c.get("code").asText()))
                .andExpect(jsonPath("$.checkpoint").value("Terrace"));
        postAs(guard, "/api/gate/patrol/scan", Map.of("code", "000000".equals(c.get("code").asText()) ? "111111" : "000000"))
                .andExpect(status().isBadRequest());
        getAs(guard, "/api/gate/report").andExpect(status().isForbidden());
        getAs(loginAs(Role.ADMIN), "/api/gate/report").andExpect(status().isOk()).andExpect(jsonPath("$.patrol_scans").exists());
    }

    @Test
    @DisplayName("SOS: a resident raises it, the guards are alerted and acknowledge it")
    void sos() throws Exception {
        String guard = loginAs(Role.SECURITY);
        String resident = loginAs(createFlat().getOwnerContact());
        String id = body(postAs(resident, "/api/sos", Map.of("kind", "medical")).andExpect(status().isCreated()))
                .get("id").asText();
        getAs(guard, "/api/notifications").andExpect(jsonPath("$[0].params.event").value("sos"));
        postAs(guard, "/api/sos/" + id + "/acknowledge", null).andExpect(jsonPath("$.status").value("acknowledged"));
        postAs(guard, "/api/sos/" + id + "/acknowledge", null).andExpect(status().isConflict());
        postAs(resident, "/api/sos/" + id + "/resolve", null).andExpect(jsonPath("$.status").value("resolved"));
    }

    @Test
    @DisplayName("amenities: bookings within hours, no clashes, cancellable by the flat")
    void amenities() throws Exception {
        String amenity = body(postAs(loginAs(Role.ADMIN), "/api/amenities",
                Map.of("name", "Clubhouse", "open_time", "08:00", "close_time", "22:00", "max_hours", 3))
                .andExpect(status().isCreated())).get("id").asText();
        String a = loginAs(createFlat().getOwnerContact());
        String b = loginAs(createFlat().getOwnerContact());
        String day = today().plusDays(3).toString();
        String booking = body(postAs(a, "/api/amenities/" + amenity + "/bookings",
                Map.of("booking_date", day, "start_time", "10:00", "end_time", "12:00"))
                .andExpect(status().isCreated())).get("id").asText();
        postAs(b, "/api/amenities/" + amenity + "/bookings",
                Map.of("booking_date", day, "start_time", "11:00", "end_time", "13:00")).andExpect(status().isConflict());
        postAs(b, "/api/amenities/" + amenity + "/bookings",
                Map.of("booking_date", day, "start_time", "06:00", "end_time", "07:00")).andExpect(status().isBadRequest());
        postAs(b, "/api/amenities/" + amenity + "/bookings",
                Map.of("booking_date", day, "start_time", "12:00", "end_time", "16:00")).andExpect(status().isBadRequest());
        postAs(b, "/api/bookings/" + booking + "/cancel", null).andExpect(status().isNotFound());
        postAs(a, "/api/bookings/" + booking + "/cancel", null).andExpect(status().isNoContent());
        postAs(b, "/api/amenities/" + amenity + "/bookings",
                Map.of("booking_date", day, "start_time", "11:00", "end_time", "13:00")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("parking: the committee assigns a slot, the flat sees only its own")
    void parking() throws Exception {
        Flat flat = createFlat();
        String label = "P" + nextSeq();
        postAs(loginAs(Role.ADMIN), "/api/parking", Map.of("label", label, "kind", "four_wheeler",
                "flat", flat.getWing() + "-" + flat.getFlatNumber())).andExpect(status().isCreated());
        getAs(loginAs(flat.getOwnerContact()), "/api/parking")
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].label").value(label.toUpperCase()));
    }

    @Test
    @DisplayName("polls: one vote per flat, changeable while open, refused once closed")
    void polls() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String id = body(postAs(admin, "/api/polls", Map.of("question", "Paint the gate?",
                "options", List.of("Yes", "No"), "closes_on", today().plusDays(5).toString()))
                .andExpect(status().isCreated())).get("id").asText();
        Flat flat = createFlat();
        String owner = loginAs(flat.getOwnerContact());
        postAs(owner, "/api/polls/" + id + "/vote", Map.of("option", 0)).andExpect(jsonPath("$.votes[0]").value(1));
        postAs(owner, "/api/polls/" + id + "/vote", Map.of("option", 1))
                .andExpect(jsonPath("$.votes[0]").value(0)).andExpect(jsonPath("$.votes[1]").value(1))
                .andExpect(jsonPath("$.my_vote").value(1));
        postAs(admin, "/api/polls/" + id + "/close", null).andExpect(jsonPath("$.open").value(false));
        postAs(owner, "/api/polls/" + id + "/vote", Map.of("option", 0)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("child exit: the flat is told")
    void childExit() throws Exception {
        Flat flat = createFlat();
        String resident = loginAs(flat.getOwnerContact());
        postAs(loginAs(Role.SECURITY), "/api/gate/child-exit",
                Map.of("wing", flat.getWing(), "flat_number", flat.getFlatNumber(), "child_name", "Aarav"))
                .andExpect(status().isNoContent());
        getAs(resident, "/api/notifications").andExpect(jsonPath("$[0].params.event").value("child_exit"));
    }

    @Test
    @DisplayName("complaints carry a helpdesk deadline")
    void complaintDeadline() throws Exception {
        String resident = loginAs(createFlat().getOwnerContact());
        postAs(resident, "/api/complaints", Map.of("title", "Leak", "description", "Water leaking in the lobby",
                "category", "plumbing", "priority", "urgent"))
                .andExpect(jsonPath("$.due_at").exists()).andExpect(jsonPath("$.overdue").value(false));
    }
}
