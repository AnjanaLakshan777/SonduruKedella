package com.sondurukedella.clients.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

// Keep this in sync with the regex in client-dialog.tsx.
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private static final Pattern PHONE = Pattern.compile("[0-9 +]{9,15}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PHONE.matcher(value.strip()).matches();
    }
}
