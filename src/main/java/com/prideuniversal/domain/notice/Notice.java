package com.prideuniversal.domain.notice;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A notice on the society board: circulars, meeting summons, emergencies.
 *
 * <p>The rules around it live in {@code NoticeService}.
 *
 * <h2>The author is two columns, on purpose</h2>
 * {@code published_by} is the author's name <em>as it was when they posted</em>;
 * {@code published_by_id} is who they are. A notice is a record of what was
 * said and by whom at the time — if the secretary changes next year, last
 * year's AGM notice must still say it came from the secretary who signed it.
 * The id is mapped as a plain {@code UUID}, not a {@code @ManyToOne User},
 * because nothing here ever needs the live user row: loading one would be an
 * extra query to fetch a name we already stored. The FK in the schema still
 * guarantees it points at a real account ({@code ON DELETE SET NULL} keeps the
 * notice if the account is removed).
 *
 * <h2>Nothing references a notice</h2>
 * Unlike {@code Flat}, no other table has a foreign key to {@code notice}, so
 * DELETE removes the row.
 */
@Entity
@Table(name = "notice")
@Getter
@Setter
public class Notice extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "notice_type", nullable = false, length = 24)
    private NoticeType noticeType = NoticeType.GENERAL;

    @Column(nullable = false, length = 16)
    private NoticeScope scope = NoticeScope.BOTH;

    /** Author's display name, snapshotted at posting time. */
    @Column(name = "published_by", length = 160)
    private String publishedBy;

    /** Author's user id. Set from the token, never from the request. */
    @Column(name = "published_by_id")
    private UUID publishedById;

    /** False means draft: visible to the people who write notices, nobody else. */
    @Column(name = "is_published", nullable = false)
    private boolean published = true;

    @Column(nullable = false)
    private boolean pinned = false;

    /** Not writable through the API yet — file uploads are entry 009. */
    @Column(name = "attachment_url", length = 512)
    private String attachmentUrl;

    @Column(name = "meeting_date")
    private LocalDate meetingDate;

    @Column(name = "meeting_venue", length = 255)
    private String meetingVenue;
}
