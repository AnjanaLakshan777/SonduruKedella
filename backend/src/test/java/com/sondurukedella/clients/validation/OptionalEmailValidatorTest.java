package com.sondurukedella.clients.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OptionalEmailValidatorTest {

    private final OptionalEmailValidator validator = new OptionalEmailValidator();

    @Test
    void nullIsFineBecauseEmailIsOptional() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    // blank values get saved as null, so they shouldn't fail validation
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    void blankValuesAreAccepted(String email) {
        assertThat(validator.isValid(email, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dilani.j@gmail.com", "admin@rajaratatrust.lk", "  sanjeewa.r@yahoo.com  "})
    void acceptsAddressesTheClientFormAccepts(String email) {
        assertThat(validator.isValid(email, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name@host", "no-at-sign.lk", "two words@gmail.com", "@gmail.com", "x"})
    void rejectsInvalidAddresses(String email) {
        assertThat(validator.isValid(email, null)).isFalse();
    }
}
