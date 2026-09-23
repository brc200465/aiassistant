package com.example.aiassistant.dto;

import lombok.Data;

@Data
public class AiChatTurn{

    private Long userMessageId;

    private String userContent;

    private String assistantContent;
}