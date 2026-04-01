package com.example.aiassistant.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Conversation {
    private Long id;
    private Long userId;
    private String title;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
