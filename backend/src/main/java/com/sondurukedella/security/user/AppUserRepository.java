package com.sondurukedella.security.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    // pass the lower-cased username; lower() also catches old mixed-case rows
    @Query("select u from AppUser u where lower(u.username) = :username")
    Optional<AppUser> findByUsername(String username);

    @Query("select count(u) > 0 from AppUser u where lower(u.username) = :username")
    boolean existsByUsername(String username);

    // pass the normalized email
    Optional<AppUser> findByEmail(String email);

    @Query("select count(u) > 0 from AppUser u where u.email = :email and (:excludeId is null or u.id <> :excludeId)")
    boolean existsByEmail(String email, Long excludeId);

    // includes disabled ones, so the bootstrap only ever runs once
    boolean existsByRole(UserRole role);

    // runs on every request (SessionUserFilter); empty if the user is gone or disabled
    @Query("select u.authVersion from AppUser u where u.id = :id and u.enabled = true")
    Optional<Long> findActiveAuthVersion(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(long id);

    // Locks all active Super Admins in id order (no deadlocks). Every account change takes this lock
    // first, so two admins can't both deactivate each other and leave nobody.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.role = :role and u.enabled = true order by u.id")
    List<AppUser> lockActiveByRole(UserRole role);
}
