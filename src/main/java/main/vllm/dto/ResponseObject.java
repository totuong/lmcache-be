package main.vllm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseObject<T> {
    private String status;
    private String message;
    private T data;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ResponseObject<T> success(T data, String message) {
        return ResponseObject.<T>builder()
                .status("SUCCESS")
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ResponseObject<T> success(T data) {
        return success(data, "Operation completed successfully");
    }

    public static <T> ResponseObject<T> error(String message) {
        return ResponseObject.<T>builder()
                .status("ERROR")
                .message(message)
                .build();
    }
}
