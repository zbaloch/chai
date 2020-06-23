package com.chaihq.webapp.validator;


import com.chaihq.webapp.models.User;
import com.chaihq.webapp.services.UserService;
import com.chaihq.webapp.utilities.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import java.util.regex.Pattern;

@Component
public class UserValidator implements Validator {
    @Autowired
    private UserService userService;

    @Override
    public boolean supports(Class<?> aClass) {
        return User.class.equals(aClass);
    }

    @Override
    public void validate(Object o, Errors errors) {
        User user = (User) o;

        // ValidationUtils.rejectIfEmptyOrWhitespace(errors, "firstName", "required.field");
        // ValidationUtils.rejectIfEmptyOrWhitespace(errors, "lastName", "required.field");

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "username", "required.field");

        if (Pattern.matches(Constants.EMAIL_REGEX, user.getUsername())) {
            errors.rejectValue("username", "email.invalid");
            System.out.println("email.invalid");
        }


        if (userService.findByUsername(user.getUsername()) != null) {
            errors.rejectValue("username", "email.taken");
            System.out.println("email.taken");
        }

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "required.field");
        if (user.getPassword().length() < 8 || user.getPassword().length() > 32) {
            errors.rejectValue("password", "password.size");
            System.out.println("password.size");
        }
    }
}
