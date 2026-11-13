package com.prideuniversal.domain.meeting;

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

class MeetingApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("residents and treasurers cannot schedule; the secretary can")
    void whoSchedules() throws Exception {
        postAs(loginAsNewResident(), "/api/meetings", meeting("AGM", "both")).andExpect(status().isForbidden());
        postAs(loginAs(Role.TREASURER), "/api/meetings", meeting("AGM", "both")).andExpect(status().isForbidden());
        postAs(loginAs(Role.SECRETARY), "/api/meetings", meeting("AGM 2026", "both"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("scheduled"));
    }

    @Test
    @DisplayName("a wing B meeting is invisible to wing A and visible to the committee")
    void wingScoped() throws Exception {
        String id = body(postAs(loginAs(Role.SECRETARY), "/api/meetings", meeting("Wing B lift", "wing_b")))
                .get("id").asText();

        String wingA = loginAs(residentOfWing("A"));
        assertThat(body(getAs(wingA, "/api/meetings").andExpect(status().isOk())).toString()).doesNotContain(id);
        getAs(wingA, "/api/meetings/" + id).andExpect(status().isNotFound());

        getAs(loginAs(residentOfWing("B")), "/api/meetings/" + id).andExpect(status().isOk());
        getAs(loginAs(Role.COMMITTEE_MEMBER), "/api/meetings/" + id).andExpect(status().isOk());
    }

    @Test
    @DisplayName("recording attendance above the total is a 400")
    void attendanceCannotExceedTotal() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        JsonNode m = body(postAs(secretary, "/api/meetings", meeting("SGM", "both")));

        Map<String, Object> record = meeting("SGM", "both");
        record.put("status", "completed");
        record.put("total_members", 40);
        record.put("members_present", 41);
        record.put("version", m.get("version").asLong());
        putAs(secretary, "/api/meetings/" + m.get("id").asText(), record).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resolutions are numbered within the meeting and returned with it")
    void resolutions() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        String id = body(postAs(secretary, "/api/meetings", meeting("AGM", "both"))).get("id").asText();

        postAs(secretary, "/api/meetings/" + id + "/resolutions",
                Map.of("title", "Approve accounts", "status", "passed", "votes_for", 30))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resolution_number").value("R-1"));
        postAs(secretary, "/api/meetings/" + id + "/resolutions", Map.of("title", "Repaint", "status", "deferred"))
                .andExpect(jsonPath("$.resolution_number").value("R-2"));

        getAs(loginAsNewResident(), "/api/meetings/" + id)
                .andExpect(jsonPath("$.resolutions.length()").value(2))
                .andExpect(jsonPath("$.resolutions[1].status").value("deferred"));
    }

    private static Map<String, Object> meeting(String title, String scope) {
        Map<String, Object> body = new HashMap<>();
        body.put("meeting_type", "agm");
        body.put("title", title);
        body.put("scope", scope);
        body.put("scheduled_date", "2026-12-20");
        body.put("scheduled_time", "10:00");
        body.put("venue", "Clubhouse");
        return body;
    }
}
