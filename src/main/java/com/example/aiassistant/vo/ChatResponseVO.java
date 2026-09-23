package com.example.aiassistant.vo;

import lombok.Data;

@Data
public class ChatResponseVO {
    private Long conversationId;
    private String userMessage;
    private String assistantMessage;
    private String requestId;
    private String status;
    private String errorMessage;
    private Long userMessageId;
    private Long assistantMessageId;
}
