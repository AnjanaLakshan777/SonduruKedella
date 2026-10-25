package com.sondurukedella.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.security.user.AppUser;
import com.sondurukedella.security.user.AppUserRepository;
import com.sondurukedella.security.user.UserRole;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Real app_users rows and real sign-ins through POST /api/v1/auth/login, for the security tests.
 * PASSWORD is a test-only fixture; it is never used outside the throwaway test database.
 */
public final class TestUsers {

    public static final String PASSWORD = "test-only-password-1";

    private TestUsers() {
    }

    // usernames are unique per call, so the login throttle (one per context) never mixes tests up
    public static AppUser create(AppUserRepository repository, PasswordEncoder encoder, String prefix, UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        user.setDisplayName("Test " + prefix);
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setRole(role);
        return repository.saveAndFlush(user);
    }

    public static MockHttpSession login(MockMvc mockMvc, String username) throws Exception {
        return login(mockMvc, username, PASSWORD);
    }

    public static MockHttpSession login(MockMvc mockMvc, String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf()).with(from(randomAddress()))
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    public static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    // every MockMvc request comes from 127.0.0.1; tests that fail sign-ins use their own address so
    // the per-address limit isn't shared across the whole suite
    public static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    public static String randomAddress() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }
}
