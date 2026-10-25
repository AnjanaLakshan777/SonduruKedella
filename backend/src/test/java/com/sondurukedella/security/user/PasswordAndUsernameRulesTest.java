package com.sondurukedella.security.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.support.TestEntities;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PasswordAndUsernameRulesTest {

    @Test
    void passwordsNeedTwelveCharactersAndFitInBcrypt() {
        assertThat(PasswordPolicy.problem(null)).isPresent();
        assertThat(PasswordPolicy.problem("            ")).isPresent();
        assertThat(PasswordPolicy.problem("elevenchars")).hasValueSatisfying(r -> assertThat(r).contains("12"));
        assertThat(PasswordPolicy.problem("twelve-chars")).isEmpty();
        assertThat(PasswordPolicy.problem("x".repeat(72))).isEmpty();
        assertThat(PasswordPolicy.problem("x".repeat(73))).hasValueSatisfying(r -> assertThat(r).contains("72"));
        // the limit is bytes: 30 Sinhala letters are 90 bytes in UTF-8
        assertThat(PasswordPolicy.problem("ස".repeat(30))).isPresent();
        assertThat(PasswordPolicy.problem("ස".repeat(20))).isEmpty();
    }

    @Test
    void usernamesAreNormalisedToLowerCase() {
        assertThat(Usernames.normalize("  Kamal.Perera ")).isEqualTo("kamal.perera");
        assertThat(Usernames.normalize(null)).isEmpty();
    }

    @Test
    void usernameFormat() {
        assertThat(Usernames.isValid("kamal")).isTrue();
        assertThat(Usernames.isValid("k.perera_2-a")).isTrue();
        assertThat(Usernames.isValid("ab")).isFalse();
        assertThat(Usernames.isValid(".kamal")).isFalse();
        assertThat(Usernames.isValid("kamal perera")).isFalse();
        assertThat(Usernames.isValid("kamal@site")).isFalse();
        assertThat(Usernames.isValid("a".repeat(50))).isTrue();
        assertThat(Usernames.isValid("a".repeat(51))).isFalse();
    }

    @Test
    void theSessionPrincipalNeverCarriesThePasswordHash() {
        AppUser user = TestEntities.create(AppUser.class, Map.of("id", 7L, "username", "kamal", "displayName", "Kamal",
                "passwordHash", "{bcrypt}$2a$10$hash", "role", UserRole.ADMIN, "enabled", true));
        AppUserPrincipal checking = AppUserPrincipal.withPassword(user);
        assertThat(checking.getPassword()).isNotNull();
        assertThat(checking.withoutPassword().getPassword()).isNull();
        assertThat(AppUserPrincipal.of(user).getPassword()).isNull();
        assertThat(checking.toString()).doesNotContain("bcrypt");
        assertThat(checking.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }
}
