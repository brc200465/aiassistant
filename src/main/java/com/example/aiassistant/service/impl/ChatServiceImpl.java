package com.example.aiassistant.service.impl;

import com.example.aiassistant.dto.ChatSendDTO;
import com.example.aiassistant.entity.Conversation;
import com.example.aiassistant.entity.Message;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.mapper.ConversationMapper;
import com.example.aiassistant.mapper.MessageMapper;
import com.example.aiassistant.service.AiService;
import com.example.aiassistant.service.ChatService;
import com.example.aiassistant.vo.ChatResponseVO;
import com.example.aiassistant.vo.MessageVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.ArrayList;

@Service
public class ChatServiceImpl implements ChatService{
    
    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private AiService aiService;

    @Override
    public ChatResponseVO sendMessage(Long userId,ChatSendDTO dto){
        Conversation conversation=conversationMapper.findById(dto.getConversationId());
        if(conversation==null)
            throw new BusinessException("会话不存在");

        if(!conversation.getUserId().equals(userId))
            throw new BusinessException("无权访问该会话");

        String content=dto.getContent().trim();
        if(content.isEmpty())
            throw new BusinessException("消息内容不能为空");

        Message userMessage=new Message();
        userMessage.setConversationId(dto.getConversationId());
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setTokenCount(null);
        messageMapper.insert(userMessage);

        String reply=aiService.generateReply(content);

        Message assistantMessage=new Message();
        assistantMessage.setConversationId(dto.getConversationId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(reply);
        assistantMessage.setTokenCount(null);
        messageMapper.insert(assistantMessage);

        conversationMapper.updateLastMessageTime(dto.getConversationId());

        ChatResponseVO vo=new ChatResponseVO();
        vo.setConversationId(dto.getConversationId());
        vo.setUserMessage(content);
        vo.setAssistantMessage(reply);
        return vo;
    }

    @Override
    public List<MessageVO>listMessages(Long userId,Long conversationId){
        Conversation conversation=conversationMapper.findById(conversationId);
        if(conversation==null)
            throw new BusinessException("会话不存在");

        if(!conversation.getUserId().equals(userId))
            throw new BusinessException("无权访问该会话");

        List<Message>messagelist=messageMapper.findByConversationId(conversationId);
        List<MessageVO>result=new ArrayList<>();

        for(Message message:messagelist){
            MessageVO vo=new MessageVO();
            BeanUtils.copyProperties(message,vo);
            result.add(vo);
        }
        return result;
    }
}
