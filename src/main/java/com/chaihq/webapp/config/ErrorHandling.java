package com.chaihq.webapp.config;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Looking up something that doesn't exist is a 404, not a 500 with an internal error.
 * Spring Boot's error page shows no stack traces or exception messages (its default).
 */
@ControllerAdvice
public class ErrorHandling {

    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
