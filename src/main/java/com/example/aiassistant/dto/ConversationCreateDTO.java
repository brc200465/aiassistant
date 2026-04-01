package com.example.aiassistant.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConversationCreateDTO{
    @Size(max=100,message="会话标题长度不能超过100")
    private String title;
}
