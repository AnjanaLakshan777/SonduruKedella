package com.sondurukedella.clients.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// e.g. "071 234 5678" or "+94 71 234 5678". Use @NotNull as well if the field is required.
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    String message() default "Enter a phone number, e.g. 071 234 5678.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
