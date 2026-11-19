package com.prideuniversal.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollVoteRepository extends JpaRepository<PollVote, UUID> {

    List<PollVote> findByPollId(UUID pollId);

    Optional<PollVote> findByPollIdAndFlatId(UUID pollId, UUID flatId);
}
