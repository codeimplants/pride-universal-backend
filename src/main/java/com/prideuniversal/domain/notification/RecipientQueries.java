package com.prideuniversal.domain.notification;

import com.prideuniversal.common.Role;
import com.prideuniversal.domain.user.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Who an audience is: the ids of active accounts, read straight from the user table. */
public interface RecipientQueries extends Repository<User, UUID> {

    @Query("select u.id from User u where u.active = true")
    List<UUID> allActive();

    @Query("select u.id from User u where u.active = true and u.flat.wing = :wing")
    List<UUID> activeInWing(@Param("wing") String wing);

    @Query("select u.id from User u where u.active = true and u.flat.id = :flatId")
    List<UUID> activeInFlat(@Param("flatId") UUID flatId);

    @Query("select u.id from User u where u.active = true and u.role in :roles")
    List<UUID> activeWithRoles(@Param("roles") Collection<Role> roles);

    @Query("select u.id from User u where u.active = true and u.id = :id")
    List<UUID> activeById(@Param("id") UUID id);
}
