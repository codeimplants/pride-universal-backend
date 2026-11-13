package com.prideuniversal.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    // TODO(entry 008): the queries the notice board needs.
    //
    // The list endpoint has two audiences (see NoticeService.list()), and both
    // want the same order: pinned first, then newest first. The schema already
    // has an index built for exactly that order - look at idx_notice_pinned in
    // V1__initial_schema.sql before you name your methods.
    //
    // FlatRepository shows both styles: derived query names, and @Query when a
    // name would be unreadable. Pick whichever reads better; you will have to
    // defend the choice.
    //
    // Filter in the query, not in Java. Loading every notice and dropping
    // drafts with .stream().filter(...) would pass the tests - and would also
    // ship every draft out of the database on every resident's request.
}
