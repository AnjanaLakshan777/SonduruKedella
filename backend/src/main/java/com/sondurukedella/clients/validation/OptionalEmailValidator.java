package com.sondurukedella.clients.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

// Same loose check as client-dialog.tsx. The service trims the value before saving it.
public class OptionalEmailValidator implements ConstraintValidator<OptionalEmail, String> {

    private static final Pattern EMAIL = Pattern.compile("\\S+@\\S+\\.\\S+");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String email = ClientText.trimToNull(value);
        return email == null || EMAIL.matcher(email).matches();
    }
}
