package com.prideuniversal.domain.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MemberBusinessRepository extends JpaRepository<MemberBusiness, UUID> {

    List<MemberBusiness> findAllByOrderByUpdatedDateDesc();

    List<MemberBusiness> findAllByStatusOrOwnerUserIdOrderByUpdatedDateDesc(BusinessStatus status, UUID ownerUserId);

    long countByOwnerUserIdAndStatusIn(UUID ownerUserId, Collection<BusinessStatus> statuses);
}
