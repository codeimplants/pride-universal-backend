package com.prideuniversal.domain.meeting.dto;

import com.prideuniversal.domain.meeting.Meeting;
import com.prideuniversal.domain.meeting.MeetingStatus;
import com.prideuniversal.domain.meeting.MeetingType;
import com.prideuniversal.domain.meeting.Resolution;
import com.prideuniversal.domain.meeting.ResolutionStatus;
import com.prideuniversal.domain.notice.NoticeScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class MeetingDtos {

    private MeetingDtos() {}

    /**
     * Create and update. Scheduling sends the first block; recording the meeting
     * afterwards sends the whole record back with status, minutes and attendance.
     */
    public record MeetingRequest(
            @NotNull(message = "Meeting type is required") MeetingType meetingType,
            @NotBlank(message = "Title is required") @Size(max = 255, message = "Title is too long") String title,
            NoticeScope scope,
            @NotNull(message = "Date is required") LocalDate scheduledDate,
            @Size(max = 16, message = "Time is too long") String scheduledTime,
            @Size(max = 255, message = "Venue is too long") String venue,
            @Size(max = 20_000, message = "Agenda is too long") String agenda,
            @Size(max = 160) String chairperson,
            @Size(max = 160) String secretary,
            MeetingStatus status,
            @Size(max = 100_000, message = "Minutes are too long") String minutes,
            Boolean quorumAchieved,
            @Min(value = 0, message = "Cannot be negative") @Max(value = 10_000) Integer totalMembers,
            @Min(value = 0, message = "Cannot be negative") @Max(value = 10_000) Integer membersPresent,
            Long version
    ) {}

    public record ResolutionRequest(
            @NotBlank(message = "Title is required") @Size(max = 255, message = "Title is too long") String title,
            @Size(max = 20_000, message = "Description is too long") String description,
            ResolutionStatus status,
            @Min(value = 0, message = "Cannot be negative") int votesFor,
            @Min(value = 0, message = "Cannot be negative") int votesAgainst,
            @Min(value = 0, message = "Cannot be negative") int votesAbstained,
            Long version
    ) {}

    public record ResolutionResponse(
            UUID id,
            UUID meetingId,
            String resolutionNumber,
            String title,
            String description,
            ResolutionStatus status,
            int votesFor,
            int votesAgainst,
            int votesAbstained,
            Instant createdDate,
            Long version
    ) {
        public static ResolutionResponse from(Resolution r) {
            return new ResolutionResponse(r.getId(), r.getMeetingId(), r.getResolutionNumber(), r.getTitle(),
                    r.getDescription(), r.getStatus(), r.getVotesFor(), r.getVotesAgainst(),
                    r.getVotesAbstained(), r.getCreatedDate(), r.getVersion());
        }
    }

    /** {@code resolutions} is filled on single reads only; lists omit it. */
    public record MeetingResponse(
            UUID id,
            MeetingType meetingType,
            String title,
            NoticeScope scope,
            LocalDate scheduledDate,
            String scheduledTime,
            String venue,
            String agenda,
            MeetingStatus status,
            String minutes,
            String minutesUrl,
            Boolean quorumAchieved,
            Integer totalMembers,
            Integer membersPresent,
            String chairperson,
            String secretary,
            Instant createdDate,
            Instant updatedDate,
            Long version,
            List<ResolutionResponse> resolutions
    ) {
        public static MeetingResponse from(Meeting m, List<ResolutionResponse> resolutions) {
            return new MeetingResponse(m.getId(), m.getMeetingType(), m.getTitle(), m.getScope(),
                    m.getScheduledDate(), m.getScheduledTime(), m.getVenue(), m.getAgenda(), m.getStatus(),
                    m.getMinutes(), m.getMinutesUrl(), m.getQuorumAchieved(), m.getTotalMembers(),
                    m.getMembersPresent(), m.getChairperson(), m.getSecretary(), m.getCreatedDate(),
                    m.getUpdatedDate(), m.getVersion(), resolutions);
        }
    }
}
