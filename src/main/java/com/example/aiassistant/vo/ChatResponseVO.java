package com.example.aiassistant.vo;

import lombok.Data;

@Data
public class ChatResponseVO {
    private Long conversationId;
    private String userMessage;
    private String assistantMessage;
}
