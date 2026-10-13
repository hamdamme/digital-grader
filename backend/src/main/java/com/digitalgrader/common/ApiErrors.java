package com.digitalgrader.common;

import java.io.IOException;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    public static Map<String, Object> body(HttpStatus status, String message) {
        return Map.of("status", status.value(), "error", status.getReasonPhrase(), "message", message);
    }
    public static void write(ObjectMapper mapper, HttpServletResponse response, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), body(status, status == HttpStatus.UNAUTHORIZED ? "Authentication failed" : "Access denied"));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException error) { return ResponseEntity.status(error.status()).body(body(error.status(), error.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception error) {
        // Never echo rejected values: requests can contain passwords and tokens.
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "Invalid request fields"));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge(Exception error) { return ResponseEntity.status(413).body(body(HttpStatus.PAYLOAD_TOO_LARGE,"ZIP must be at most 50 MiB")); }
    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> missing(Exception error) { return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST,"Invalid request fields")); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied(AccessDeniedException error) {
        return ResponseEntity.status(403).body(body(HttpStatus.FORBIDDEN, "Access denied"));
    }
}
