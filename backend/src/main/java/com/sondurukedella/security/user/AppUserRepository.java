package com.sondurukedella.security.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    // Pass a normalized (lower-case) username. Matching on lower() also finds rows written before V5
    // in mixed case; uq_app_users_username_lower makes it unique and indexed.
    @Query("select u from AppUser u where lower(u.username) = :username")
    Optional<AppUser> findByUsername(String username);

    @Query("select count(u) > 0 from AppUser u where lower(u.username) = :username")
    boolean existsByUsername(String username);

    // counts disabled accounts too, so the bootstrap never runs again once a Super Admin was made
    boolean existsByRole(UserRole role);

    // Checked on every request of a signed-in session: empty when the account is gone or disabled.
    @Query("select u.authVersion from AppUser u where u.id = :id and u.enabled = true")
    Optional<Long> findActiveAuthVersion(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(long id);

    // SELECT ... FOR UPDATE on every active Super Admin, always in id order so two account changes
    // can't deadlock. Account changes take this first, which serialises them and keeps the
    // "at least one active Super Admin" count true.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.role = :role and u.enabled = true order by u.id")
    List<AppUser> lockActiveByRole(UserRole role);
}
