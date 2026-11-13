package com.prideuniversal.domain.notice;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * The notice board's rules, one per test. When a test fails, read its
 * {@code @DisplayName} first — the name is the rule.
 *
 * <p>Isolation note: these tests share one database and one notice board, so
 * no test asserts on the <em>whole</em> list. Each asserts only on notices it
 * created itself ("my notice is / is not in the list", "my pinned notice comes
 * before my unpinned one").
 */
class NoticeApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NoticeRepository noticeRepository;

    // =====================================================================
    // Who sees what - drafts, and wing scope
    // =====================================================================

    @Test
    @DisplayName("a resident sees a published, society-wide notice")
    void residentSeesAPublishedNotice() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        String id = postNotice(secretary, noticeMap("Water cut on Sunday")).get("id").asText();

        String resident = loginAsNewResident();

        assertThat(listIds(resident)).contains(id);
        mockMvc.perform(get("/api/notices/" + id).header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Water cut on Sunday"));
    }

    @Test
    @DisplayName("a draft never appears in a resident's list")
    void draftIsHiddenFromAResidentsList() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        String id = postNotice(secretary, draftMap("Draft: AGM agenda")).get("id").asText();

        assertThat(listIds(loginAsNewResident())).doesNotContain(id);
    }

    @Test
    @DisplayName("a draft is 404 to a resident, not 403 - its existence is the secret")
    void draftIsNotFoundToAResident() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        String id = postNotice(secretary, draftMap("Draft: special levy")).get("id").asText();

        // A 403 would say "this exists, and you can't have it" - which, for an
        // unannounced notice, is the very thing being kept back.
        mockMvc.perform(get("/api/notices/" + id).header("Authorization", bearer(loginAsNewResident())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("people who write notices see drafts - including each other's")
    void writersSeeDrafts() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        String id = postNotice(secretary, draftMap("Draft: festival plan")).get("id").asText();

        String committeeMember = loginAs(Role.COMMITTEE_MEMBER);

        assertThat(listIds(committeeMember)).contains(id);
        mockMvc.perform(get("/api/notices/" + id).header("Authorization", bearer(committeeMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_published").value(false));
    }

    @Test
    @DisplayName("a wing A notice reaches wing A residents and is invisible to wing B")
    void wingScopedNoticeOnlyReachesThatWing() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        Map<String, Object> body = noticeMap("Wing A lift maintenance");
        body.put("scope", "wing_a");
        String id = postNotice(secretary, body).get("id").asText();

        String wingA = loginAs(residentOfWing("A"));
        String wingB = loginAs(residentOfWing("B"));

        assertThat(listIds(wingA)).contains(id);
        assertThat(listIds(wingB)).doesNotContain(id);
        mockMvc.perform(get("/api/notices/" + id).header("Authorization", bearer(wingB)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("pinned notices come first, even when older")
    void pinnedNoticesComeFirst() throws Exception {
        String secretary = loginAs(Role.SECRETARY);

        Map<String, Object> older = noticeMap("Pinned: society rules");
        older.put("pinned", true);
        String pinnedId = postNotice(secretary, older).get("id").asText();
        String newerId = postNotice(secretary, noticeMap("Newer, unpinned")).get("id").asText();

        List<String> ids = listIds(loginAsNewResident());

        // Newest-first alone would put newerId ahead. Only pinned-first puts
        // the older notice in front.
        assertThat(ids).contains(pinnedId, newerId);
        assertThat(ids.indexOf(pinnedId)).isLessThan(ids.indexOf(newerId));
    }

    @Test
    @DisplayName("publishing a draft makes it visible to residents")
    void publishingADraftMakesItVisible() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        JsonNode draft = postNotice(secretary, draftMap("Draft: parking rules"));
        String id = draft.get("id").asText();
        String resident = loginAsNewResident();
        assertThat(listIds(resident)).doesNotContain(id);

        Map<String, Object> publish = noticeMap("Parking rules");
        publish.put("version", draft.get("version").asLong());
        mockMvc.perform(put("/api/notices/" + id)
                        .header("Authorization", bearer(secretary))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publish)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_published").value(true));

        assertThat(listIds(resident)).contains(id);
    }

    // =====================================================================
    // Who may write
    // =====================================================================

    @Test
    @DisplayName("a resident cannot post a notice")
    void residentCannotPostNotices() throws Exception {
        mockMvc.perform(post("/api/notices")
                        .header("Authorization", bearer(loginAsNewResident()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noticeMap("Not allowed"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("a treasurer cannot post a notice - management is not communications")
    void treasurerCannotPostNotices() throws Exception {
        // The treasurer is on the committee and can see the ledger, but
        // speaking for the society is a different permission. Copying
        // FlatService's role list, or using isManagement(), fails here.
        mockMvc.perform(post("/api/notices")
                        .header("Authorization", bearer(loginAs(Role.TREASURER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noticeMap("Not allowed either"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a committee member can post a notice -> 201 with the new record")
    void committeeMemberCanPostNotices() throws Exception {
        mockMvc.perform(post("/api/notices")
                        .header("Authorization", bearer(loginAs(Role.COMMITTEE_MEMBER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noticeMap("Diwali celebration"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Diwali celebration"))
                .andExpect(jsonPath("$.notice_type").value("general"))
                .andExpect(jsonPath("$.is_published").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("the author comes from the token; an author in the body is ignored")
    void authorComesFromTheTokenNotTheBody() throws Exception {
        String mobile = newUserMobile(Role.CHAIRMAN);
        User chairman = userRepository.findByMobile(mobile).orElseThrow();
        String token = loginAs(mobile);

        Map<String, Object> forged = noticeMap("Signed notice");
        forged.put("published_by", "Someone Else Entirely");
        forged.put("published_by_id", UUID.randomUUID().toString());

        JsonNode created = postNotice(token, forged);

        assertThat(created.get("published_by").asText()).isEqualTo(chairman.getDisplayName());
        Notice saved = noticeRepository.findById(UUID.fromString(created.get("id").asText()))
                .orElseThrow();
        assertThat(saved.getPublishedById()).isEqualTo(chairman.getId());
    }

    // =====================================================================
    // Validation
    // =====================================================================

    @Test
    @DisplayName("a blank title is reported per-field")
    void blankTitleIsAFieldError() throws Exception {
        Map<String, Object> body = noticeMap("placeholder");
        body.put("title", "  ");

        mockMvc.perform(post("/api/notices")
                        .header("Authorization", bearer(loginAs(Role.SECRETARY)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.field_errors.title").exists());
    }

    @Test
    @DisplayName("an AGM notice with no meeting date -> 400")
    void agmWithoutMeetingDateIsBadRequest() throws Exception {
        Map<String, Object> body = noticeMap("Annual General Meeting");
        body.put("notice_type", "agm");

        mockMvc.perform(post("/api/notices")
                        .header("Authorization", bearer(loginAs(Role.SECRETARY)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an AGM notice with a meeting date is accepted")
    void agmWithMeetingDateIsAccepted() throws Exception {
        Map<String, Object> body = noticeMap("Annual General Meeting 2026");
        body.put("notice_type", "agm");
        body.put("meeting_date", "2026-12-20");
        body.put("meeting_venue", "Clubhouse");

        mockMvc.perform(post("/api/notices")
                        .header("Authorization", bearer(loginAs(Role.SECRETARY)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notice_type").value("agm"))
                .andExpect(jsonPath("$.meeting_date").value("2026-12-20"));
    }

    @Test
    @DisplayName("an unknown notice type -> 400, not a database error")
    void unknownNoticeTypeIsBadRequest() throws Exception {
        Map<String, Object> body = noticeMap("Party!");
        body.put("notice_type", "party");

        mockMvc.perform(post("/api/notices")
                        .header("Authorization", bearer(loginAs(Role.SECRETARY)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // Optimistic locking - the same rules as Flat
    // =====================================================================

    @Test
    @DisplayName("an update returns a version that works for the next update")
    void updateReturnsAUsableVersion() throws Exception {
        String token = loginAs(Role.SECRETARY);
        JsonNode created = postNotice(token, noticeMap("First title"));
        String id = created.get("id").asText();

        Map<String, Object> first = noticeMap("Second title");
        first.put("version", created.get("version").asLong());
        String firstResponse = mockMvc.perform(put("/api/notices/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(created.get("version").asLong() + 1))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> second = noticeMap("Third title");
        second.put("version", objectMapper.readTree(firstResponse).get("version").asLong());
        mockMvc.perform(put("/api/notices/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Third title"));
    }

    @Test
    @DisplayName("an update with a stale version -> 409, and does not overwrite")
    void updateWithStaleVersionIsConflict() throws Exception {
        String token = loginAs(Role.SECRETARY);
        JsonNode created = postNotice(token, noticeMap("Original"));
        String id = created.get("id").asText();
        long staleVersion = created.get("version").asLong();

        Map<String, Object> first = noticeMap("First Writer");
        first.put("version", staleVersion);
        mockMvc.perform(put("/api/notices/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        Map<String, Object> second = noticeMap("Second Writer");
        second.put("version", staleVersion);
        mockMvc.perform(put("/api/notices/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("concurrent_modification"));

        assertThat(noticeRepository.findById(UUID.fromString(id)).orElseThrow().getTitle())
                .isEqualTo("First Writer");
    }

    @Test
    @DisplayName("an update with no version -> 400, never a silent overwrite")
    void updateWithoutVersionIsBadRequest() throws Exception {
        String token = loginAs(Role.SECRETARY);
        String id = postNotice(token, noticeMap("Needs a version")).get("id").asText();

        mockMvc.perform(put("/api/notices/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noticeMap("No version sent"))))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // Delete
    // =====================================================================

    @Test
    @DisplayName("a resident cannot delete a notice")
    void residentCannotDeleteNotices() throws Exception {
        String id = postNotice(loginAs(Role.SECRETARY), noticeMap("Stays up")).get("id").asText();

        mockMvc.perform(delete("/api/notices/" + id)
                        .header("Authorization", bearer(loginAsNewResident())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a deleted notice is gone - for the committee too")
    void committeeDeletesANotice() throws Exception {
        String secretary = loginAs(Role.SECRETARY);
        String id = postNotice(secretary, noticeMap("Posted by mistake")).get("id").asText();

        mockMvc.perform(delete("/api/notices/" + id).header("Authorization", bearer(secretary)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notices/" + id).header("Authorization", bearer(secretary)))
                .andExpect(status().isNotFound());
        assertThat(listIds(secretary)).doesNotContain(id);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private JsonNode postNotice(String token, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/api/notices")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    /** The ids on the board, in the order the API returned them. */
    private List<String> listIds(String token) throws Exception {
        String response = mockMvc.perform(get("/api/notices").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = new ArrayList<>();
        objectMapper.readTree(response).forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    /**
     * A published, society-wide general notice, with every defaultable field
     * sent explicitly.
     */
    private Map<String, Object> noticeMap(String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", "Details for: " + title);
        body.put("notice_type", "general");
        body.put("scope", "both");
        body.put("is_published", true);
        body.put("pinned", false);
        return body;
    }

    private Map<String, Object> draftMap(String title) {
        Map<String, Object> body = noticeMap(title);
        body.put("is_published", false);
        return body;
    }
}
