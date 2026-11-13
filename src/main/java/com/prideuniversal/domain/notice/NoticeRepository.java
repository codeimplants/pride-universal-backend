package com.prideuniversal.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Both lists use the board's order — pinned first, then newest — which is exactly
 * {@code idx_notice_pinned}. Visibility is filtered in the query, so drafts never
 * leave the database for a reader who may not see them.
 *
 * <p>Capped at 200: the board is a noticeboard, not an archive.
 */
public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    /** Writers: everything, drafts and every wing included. */
    List<Notice> findTop200ByOrderByPinnedDescCreatedDateDesc();

    /** Readers: published notices addressed to one of the given scopes. */
    List<Notice> findTop200ByPublishedTrueAndScopeInOrderByPinnedDescCreatedDateDesc(
            Collection<NoticeScope> scopes);
}
