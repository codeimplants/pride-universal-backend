package com.prideuniversal.domain.meeting;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.meeting.dto.MeetingDtos.MeetingRequest;
import com.prideuniversal.domain.meeting.dto.MeetingDtos.MeetingResponse;
import com.prideuniversal.domain.meeting.dto.MeetingDtos.ResolutionRequest;
import com.prideuniversal.domain.meeting.dto.MeetingDtos.ResolutionResponse;
import com.prideuniversal.domain.notice.NoticeScope;
import com.prideuniversal.domain.notice.WingScopes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Meetings and their resolutions.
 *
 * <p>Everyone sees the meetings addressed to them (both wings, or their own wing);
 * the committee sees every meeting. A meeting not addressed to the caller is a 404,
 * as with notices. Admin, chairman and secretary schedule and record meetings.
 */
@Service
public class MeetingService {

    private static final String CAN_MANAGE = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY')";

    private final MeetingRepository meetingRepository;
    private final ResolutionRepository resolutionRepository;
    private final WingScopes wingScopes;

    public MeetingService(MeetingRepository meetingRepository, ResolutionRepository resolutionRepository,
                          WingScopes wingScopes) {
        this.meetingRepository = meetingRepository;
        this.resolutionRepository = resolutionRepository;
        this.wingScopes = wingScopes;
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> list(MeetingStatus status) {
        return meetingRepository.search(scopesFor(AuthPrincipal.current()), status).stream()
                .map(m -> MeetingResponse.from(m, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(UUID id) {
        Meeting meeting = findVisible(id);
        return MeetingResponse.from(meeting, resolutionsOf(id));
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public MeetingResponse create(MeetingRequest request) {
        Meeting meeting = new Meeting();
        apply(request, meeting);
        if (request.status() == null) {
            meeting.setStatus(MeetingStatus.SCHEDULED);
        }
        return MeetingResponse.from(meetingRepository.save(meeting), List.of());
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public MeetingResponse update(UUID id, MeetingRequest request) {
        Meeting meeting = meetingRepository.findById(id).orElseThrow(() -> ApiException.notFound("Meeting", id));
        Versions.requireCurrent(meeting.getVersion(), request.version(), "meeting");
        apply(request, meeting);
        return MeetingResponse.from(meetingRepository.saveAndFlush(meeting), resolutionsOf(id));
    }

    /** Its resolutions go with it (ON DELETE CASCADE). */
    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public void delete(UUID id) {
        Meeting meeting = meetingRepository.findById(id).orElseThrow(() -> ApiException.notFound("Meeting", id));
        meetingRepository.delete(meeting);
    }

    // --- Resolutions ------------------------------------------------------

    /**
     * Numbered R-1, R-2, ... within the meeting. Two committee members adding one
     * at the same instant would both pick the same number; the unique constraint
     * turns the second into a 409 and they retry, which is acceptable here.
     */
    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public ResolutionResponse addResolution(UUID meetingId, ResolutionRequest request) {
        meetingRepository.findById(meetingId).orElseThrow(() -> ApiException.notFound("Meeting", meetingId));
        Resolution resolution = new Resolution();
        resolution.setMeetingId(meetingId);
        resolution.setResolutionNumber("R-" + (resolutionRepository.countByMeetingId(meetingId) + 1));
        applyResolution(request, resolution);
        return ResolutionResponse.from(resolutionRepository.saveAndFlush(resolution));
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public ResolutionResponse updateResolution(UUID meetingId, UUID resolutionId, ResolutionRequest request) {
        Resolution resolution = findResolution(meetingId, resolutionId);
        Versions.requireCurrent(resolution.getVersion(), request.version(), "resolution");
        applyResolution(request, resolution);
        return ResolutionResponse.from(resolutionRepository.saveAndFlush(resolution));
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public void deleteResolution(UUID meetingId, UUID resolutionId) {
        resolutionRepository.delete(findResolution(meetingId, resolutionId));
    }

    // ---------------------------------------------------------------------

    private Set<NoticeScope> scopesFor(AuthPrincipal caller) {
        return caller.isManagement() ? EnumSet.allOf(NoticeScope.class) : wingScopes.addressing(caller);
    }

    private Meeting findVisible(UUID id) {
        Set<NoticeScope> scopes = scopesFor(AuthPrincipal.current());
        return meetingRepository.findById(id)
                .filter(m -> scopes.contains(m.getScope()))
                .orElseThrow(() -> ApiException.notFound("Meeting", id));
    }

    private List<ResolutionResponse> resolutionsOf(UUID meetingId) {
        return resolutionRepository.findByMeetingIdOrderByCreatedDateAsc(meetingId).stream()
                .map(ResolutionResponse::from).toList();
    }

    private Resolution findResolution(UUID meetingId, UUID resolutionId) {
        return resolutionRepository.findById(resolutionId)
                .filter(r -> r.getMeetingId().equals(meetingId))
                .orElseThrow(() -> ApiException.notFound("Resolution", resolutionId));
    }

    private void apply(MeetingRequest r, Meeting m) {
        if (r.membersPresent() != null && r.totalMembers() != null && r.membersPresent() > r.totalMembers()) {
            throw ApiException.badRequest("Members present cannot exceed total members.");
        }
        m.setMeetingType(r.meetingType());
        m.setTitle(r.title().trim());
        m.setScope(r.scope() == null ? NoticeScope.BOTH : r.scope());
        m.setScheduledDate(r.scheduledDate());
        m.setScheduledTime(Text.blankToNull(r.scheduledTime()));
        m.setVenue(Text.blankToNull(r.venue()));
        m.setAgenda(Text.blankToNull(r.agenda()));
        m.setChairperson(Text.blankToNull(r.chairperson()));
        m.setSecretary(Text.blankToNull(r.secretary()));
        if (r.status() != null) {
            m.setStatus(r.status());
        }
        m.setMinutes(Text.blankToNull(r.minutes()));
        m.setQuorumAchieved(r.quorumAchieved());
        m.setTotalMembers(r.totalMembers());
        m.setMembersPresent(r.membersPresent());
    }

    private void applyResolution(ResolutionRequest r, Resolution res) {
        res.setTitle(r.title().trim());
        res.setDescription(Text.blankToNull(r.description()));
        res.setStatus(r.status() == null ? ResolutionStatus.PASSED : r.status());
        res.setVotesFor(r.votesFor());
        res.setVotesAgainst(r.votesAgainst());
        res.setVotesAbstained(r.votesAbstained());
    }
}
