package com.prideuniversal.domain.meeting;

import com.prideuniversal.common.BaseEntity;
import com.prideuniversal.domain.notice.NoticeScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A general body or committee meeting, and afterwards its record (minutes, attendance). */
@Entity
@Table(name = "meeting")
@Getter
@Setter
public class Meeting extends BaseEntity {

    @Column(name = "meeting_type", nullable = false, length = 16)
    private MeetingType meetingType;

    @Column(nullable = false, length = 255)
    private String title;

    /** Same codes as a notice's scope ({@code ck_meeting_scope}). */
    @Column(nullable = false, length = 16)
    private NoticeScope scope = NoticeScope.BOTH;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /** Free text ("10:00", "after evening aarti"), as the Base44 app stored it. */
    @Column(name = "scheduled_time", length = 16)
    private String scheduledTime;

    @Column(length = 255)
    private String venue;

    @Column(columnDefinition = "text")
    private String agenda;

    @Column(nullable = false, length = 16)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(columnDefinition = "text")
    private String minutes;

    @Column(name = "minutes_url", length = 512)
    private String minutesUrl;

    @Column(name = "quorum_achieved")
    private Boolean quorumAchieved;

    @Column(name = "total_members")
    private Integer totalMembers;

    @Column(name = "members_present")
    private Integer membersPresent;

    @Column(length = 160)
    private String chairperson;

    @Column(length = 160)
    private String secretary;
}
