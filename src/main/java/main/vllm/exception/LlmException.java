package main.vllm.exception;

import org.springframework.http.HttpStatus;

public class LlmException extends BaseException {
    
    public LlmException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY);
    }
}
