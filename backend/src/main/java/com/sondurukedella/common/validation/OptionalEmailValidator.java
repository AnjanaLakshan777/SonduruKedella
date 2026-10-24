package com.sondurukedella.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

// Same loose check the frontend forms use (client and supplier dialogs). Blank passes because the
// field is optional; the services trim and store blanks as null.
public class OptionalEmailValidator implements ConstraintValidator<OptionalEmail, String> {

    private static final Pattern EMAIL = Pattern.compile("\\S+@\\S+\\.\\S+");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return EMAIL.matcher(value.strip()).matches();
    }
}
