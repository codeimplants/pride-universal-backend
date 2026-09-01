package com.prideuniversal.domain.user;

import com.prideuniversal.common.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByMobile(String mobile);

    boolean existsByMobile(String mobile);

    /**
     * Loads the user together with their flat in one query.
     *
     * <p>{@code @EntityGraph} turns the LAZY {@code flat} association into a
     * join for this call only. Without it, rendering a list of users and
     * touching {@code user.getFlat()} fires one extra SELECT per row - the
     * N+1 problem. With it: one query, one round trip.
     */
    @EntityGraph(attributePaths = "flat")
    Optional<User> findWithFlatByMobile(String mobile);

    @EntityGraph(attributePaths = "flat")
    Optional<User> findWithFlatById(UUID id);

    @EntityGraph(attributePaths = "flat")
    List<User> findAllByRoleIn(List<Role> roles);

    @Query("select u from User u where u.flat.id = :flatId and u.active = true")
    List<User> findActiveByFlatId(@Param("flatId") UUID flatId);
}
