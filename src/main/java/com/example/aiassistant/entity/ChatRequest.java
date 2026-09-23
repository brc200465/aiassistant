package com.example.aiassistant.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatRequest {
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private Long id;
    private Long conversationId;
    private String requestId;
    private Long userMessageId;
    private Long assistantMessageId;
    private String status;
    private String errorMessage;
    private int attempt;
    private LocalDateTime leaseUntil;
}
