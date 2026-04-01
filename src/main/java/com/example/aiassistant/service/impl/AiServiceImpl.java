package com.example.aiassistant.service.impl;

import com.example.aiassistant.service.AiService;
import org.springframework.stereotype.Service;

@Service
public class AiServiceImpl implements AiService{
    
    @Override
    public String generateReply(String userMessage){
        return "这是AI的模拟回复：你刚才问的是 ->"+userMessage;
    }
}
