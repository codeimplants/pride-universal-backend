package com.prideuniversal.domain.notification;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Something sent to members. A {@code message} is the committee's words, shown as written; a
 * {@code dues_reminder} is the app's, kept as {@code params} so each reader sees it in their language
 * ({@code title} and {@code body} hold the English, for the record).
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
public class Notification extends BaseEntity {

    public static final String MESSAGE = "message";
    public static final String GATE = "gate";
    public static final String DUES_REMINDER = "dues_reminder";

    @Column(nullable = false, length = 24)
    private String kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    /** JSON, for the kinds the app writes itself. */
    @Column(columnDefinition = "text")
    private String params;

    @Column(nullable = false, length = 16)
    private String audience;

    @Column(name = "audience_value", length = 64)
    private String audienceValue;

    @Column(length = 64)
    private String screen;

    @Column(name = "sent_by", length = 160)
    private String sentBy;

    @Column(name = "sent_by_id")
    private UUID sentById;

    @Column(nullable = false)
    private int recipients;

    @Column(nullable = false)
    private int devices;

    @Column(nullable = false)
    private int delivered;
}
