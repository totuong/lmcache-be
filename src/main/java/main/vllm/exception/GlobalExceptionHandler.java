package main.vllm.exception;

import main.vllm.dto.ResponseObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ResponseObject<Void>> handleBaseException(BaseException ex) {
        log.error("Custom application error occurred: {}", ex.getMessage(), ex);
        ResponseObject<Void> response = ResponseObject.error(ex.getMessage());
        return new ResponseEntity<>(response, ex.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseObject<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled error occurred in application: {}", ex.getMessage(), ex);
        ResponseObject<Void> response = ResponseObject.error("An unexpected error occurred: " + ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
