package com.example.aiassistant.service;

import com.example.aiassistant.dto.ChatSendDTO;
import com.example.aiassistant.vo.ChatResponseVO;
import com.example.aiassistant.vo.MessageVO;

import java.util.List;

public interface ChatService {
    
    ChatResponseVO sendMessage(Long userId,ChatSendDTO dto);

    List<MessageVO>listMessages(Long userId,Long conversationId);
}
