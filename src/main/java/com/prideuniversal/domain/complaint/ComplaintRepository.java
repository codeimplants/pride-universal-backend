package com.prideuniversal.domain.complaint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {

    /** The committee's helpdesk view; a null filter matches everything. */
    @Query("""
            select c from Complaint c
            where (:status is null or c.status = :status)
              and (:wing is null or c.wing = :wing)
            order by c.createdDate desc
            limit 200
            """)
    List<Complaint> search(@Param("status") ComplaintStatus status, @Param("wing") String wing);

    /** A resident's view: their flat's tickets, plus any they raised themselves. */
    @Query("""
            select c from Complaint c
            where (c.flatId = :flatId or c.raisedByUserId = :userId)
              and (:status is null or c.status = :status)
            order by c.createdDate desc
            limit 200
            """)
    List<Complaint> forResident(@Param("flatId") UUID flatId,
                                @Param("userId") UUID userId,
                                @Param("status") ComplaintStatus status);

    long countByStatus(ComplaintStatus status);

    long countByFlatIdAndStatusIn(UUID flatId, java.util.Collection<ComplaintStatus> statuses);

    @Query(value = "select nextval('complaint_number_seq')", nativeQuery = true)
    long nextNumber();
}
