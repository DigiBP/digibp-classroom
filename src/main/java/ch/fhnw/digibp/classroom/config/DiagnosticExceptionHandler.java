package ch.fhnw.digibp.classroom.config;

import org.cibseven.webapp.exception.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DiagnosticExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(DiagnosticExceptionHandler.class);

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<String> handleSystemException(SystemException ex) {
        log.error("CIB Seven Webclient exception", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal Server Error");
    }
}
