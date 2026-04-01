package com.example.aiassistant.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {
    private Long id;
    private String title;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;
}
