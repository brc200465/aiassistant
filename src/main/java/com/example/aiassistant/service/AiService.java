package com.example.aiassistant.service;

import com.example.aiassistant.dto.AiChatMessage;

import java.util.List;

public interface AiService {
    /**
     * 最终推荐的签名：接收消息列表
     * @param uerMessage
     * @return
     */
    String generateReply(List<AiChatMessage>message);

    /**
     * 为了与当前阶段兼容，也保留一个默认单轮入口
     */
    default String generateReply(String userMessage){
        return generateReply(List.of(new AiChatMessage("user",userMessage)));
    }
}
