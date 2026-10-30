package com.sondurukedella.security.user;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "The password does not meet the rules.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return PasswordPolicy.problem(value).map(reason -> {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(reason).addConstraintViolation();
                return false;
            }).orElse(true);
        }
    }
}
