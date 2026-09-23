package com.example.aiassistant.dto;

import com.example.aiassistant.entity.ChatRequest;
import com.example.aiassistant.vo.ChatResponseVO;
import lombok.Data;

@Data
public class StartResult{

    private boolean shouldGenerate;

    private ChatRequest request;

    private ChatResponseVO response;
}