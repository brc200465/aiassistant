package com.example.aiassistant.service;

import com.example.aiassistant.dto.ConversationCreateDTO;
import com.example.aiassistant.vo.ConversationVO;

import java.util.List;


public interface ConversationService {

    ConversationVO createConversation(Long userId,ConversationCreateDTO dto);

    List<ConversationVO>listConversations(Long userId);

    ConversationVO getConversationDetail(Long userId,Long conversationId);
}