package com.sondurukedella.clients.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Not @Email: that rejects "   ", but a blank optional field should just be saved as null.
@Documented
@Constraint(validatedBy = OptionalEmailValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalEmail {

    String message() default "Check the email address.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
