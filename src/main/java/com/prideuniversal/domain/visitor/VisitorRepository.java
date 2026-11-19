package com.prideuniversal.domain.visitor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VisitorRepository extends JpaRepository<Visitor, UUID> {

    @Query("""
            select v from Visitor v
            where (:status is null or v.status = :status)
            order by v.createdDate desc
            limit 200
            """)
    List<Visitor> search(@Param("status") VisitorStatus status);

    @Query("""
            select v from Visitor v
            where v.flatId = :flatId
              and (:status is null or v.status = :status)
            order by v.createdDate desc
            limit 200
            """)
    List<Visitor> forFlat(@Param("flatId") UUID flatId, @Param("status") VisitorStatus status);

    @Query("select v from Visitor v where v.createdDate >= :from and v.createdDate < :to")
    List<Visitor> between(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    long countByStatus(VisitorStatus status);

    long countByFlatIdAndStatus(UUID flatId, VisitorStatus status);
}
