package com.example.aiassistant.service;

import com.example.aiassistant.dto.ChatSendDTO;
import com.example.aiassistant.dto.StartResult;
import com.example.aiassistant.vo.ChatResponseVO;

public interface ChatRequestService {

    StartResult begin(Long userId, ChatSendDTO dto);

    ChatResponseVO complete(Long conversationId, String requestId,
                            int attempt, String reply);

    ChatResponseVO fail(Long conversationId, String requestId,
                        int attempt, String errorMessage);
}