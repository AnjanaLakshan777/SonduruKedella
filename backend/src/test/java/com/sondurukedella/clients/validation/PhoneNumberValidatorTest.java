package com.sondurukedella.clients.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberValidatorTest {

    private final PhoneNumberValidator validator = new PhoneNumberValidator();

    @ParameterizedTest
    @ValueSource(strings = {"071 438 2291", "0714382291", "+94 71 438 2291", "025 226 4410", "  071 438 2291  "})
    void acceptsNumbersTheClientFormAccepts(String phone) {
        assertThat(validator.isValid(phone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "07143822", "071-438-2291", "071 438 2291 ext 4", "+94 71 438 2291 00", "abc"})
    void rejectsNumbersTheClientFormRejects(String phone) {
        assertThat(validator.isValid(phone, null)).isFalse();
    }

    @Test
    void leavesNullToNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
