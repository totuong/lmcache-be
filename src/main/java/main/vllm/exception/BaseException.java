package main.vllm.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {
    private final HttpStatus status;

    protected BaseException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    protected BaseException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }
}
