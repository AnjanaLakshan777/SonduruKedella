package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.security.user.AppUser;
import com.sondurukedella.security.user.AppUserRepository;
import com.sondurukedella.security.user.UserAdministrationService;
import com.sondurukedella.security.user.UserRole;
import com.sondurukedella.security.user.dto.UserStatusRequest;
import com.sondurukedella.support.TestUsers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

// Not @Transactional: two Super Admins really deactivating each other at the same moment. The locks
// in UserAdministrationService must let exactly one win, so an active Super Admin always remains.
// Users are named sectest-* and deleted afterwards. Throwaway DB only.
@SpringBootTest
class UserAdministrationConcurrencyIntegrationTest {

    @Autowired
    private UserAdministrationService service;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        String ours = "SELECT id FROM app_users WHERE username LIKE 'sectest-%'";
        // security_events is append-only (V5 trigger); lift it for this cleanup only
        jdbc.execute("ALTER TABLE security_events DISABLE TRIGGER trg_security_events_immutable");
        try {
            jdbc.update("DELETE FROM security_events WHERE actor_user_id IN (" + ours + ") OR target_user_id IN (" + ours + ")");
            jdbc.update("DELETE FROM app_users WHERE username LIKE 'sectest-%'");
        } finally {
            jdbc.execute("ALTER TABLE security_events ENABLE TRIGGER trg_security_events_immutable");
        }
    }

    @Test
    void twoSuperAdminsDeactivatingEachOtherCannotBothSucceed() throws Exception {
        for (int round = 0; round < 3; round++) {
            AppUser a = TestUsers.create(users, encoder, "sectest-a", UserRole.SUPER_ADMIN);
            AppUser b = TestUsers.create(users, encoder, "sectest-b", UserRole.SUPER_ADMIN);

            List<Object> outcomes = race(
                    () -> service.setStatus(a.getId(), b.getId(), new UserStatusRequest(false, b.getVersion())),
                    () -> service.setStatus(b.getId(), a.getId(), new UserStatusRequest(false, a.getVersion())));

            assertThat(outcomes).filteredOn(o -> o instanceof AccessDeniedException).hasSize(1);
            assertThat(outcomes).filteredOn(o -> !(o instanceof Throwable)).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE id IN (?, ?) AND enabled AND role = 'SUPER_ADMIN'",
                    Long.class, a.getId(), b.getId())).isOne();
        }
    }

    // runs both at once and returns each result, or the exception it threw
    private static List<Object> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Runnable task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.run();
                        return "ok";
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
