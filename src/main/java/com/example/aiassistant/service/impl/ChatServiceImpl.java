package com.example.aiassistant.service.impl;

import com.example.aiassistant.common.ErrorCode;
import com.example.aiassistant.constant.RedisKeyConstants;
import com.example.aiassistant.dto.AiChatMessage;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.ArrayList;

@Service
public class ChatServiceImpl implements ChatService{

    private static final int CONTEXT_LIMIT=10;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private AiService aiService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public ChatResponseVO sendMessage(Long userId,ChatSendDTO dto){
        Conversation conversation=conversationMapper.findById(dto.getConversationId());
        if(conversation==null)
            throw new BusinessException(ErrorCode.NOT_FOUND,"会话不存在");

        if(!conversation.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.NO_PERMISSION,"无权访问该会话");

        String content=dto.getContent().trim();
        if(content==null)
            throw new BusinessException(ErrorCode.PARAM_ERROR,"消息内容不能为空");

        Message userMessage=new Message();
        userMessage.setConversationId(dto.getConversationId());
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setTokenCount(null);
        messageMapper.insert(userMessage);

        List<Message>recentMessages=messageMapper.findRecentMessages(dto.getConversationId(),CONTEXT_LIMIT);
        
        List<AiChatMessage>aiMessages=new ArrayList<>();
        for(Message message:recentMessages){
            aiMessages.add(new AiChatMessage(message.getRole(),message.getContent()));
        }

        String reply=aiService.generateReply(aiMessages);

        Message assistantMessage=new Message();
        assistantMessage.setConversationId(dto.getConversationId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(reply);
        assistantMessage.setTokenCount(null);
        messageMapper.insert(assistantMessage);

        conversationMapper.updateLastMessageTime(dto.getConversationId());

        deleteConversationListCache(userId);

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
            throw new BusinessException(ErrorCode.NOT_FOUND,"会话不存在");

        if(!conversation.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.NO_PERMISSION,"无权访问该会话");

        List<Message>list=messageMapper.findByConversationId(conversationId);
        List<MessageVO>result=new ArrayList<>();

        for(Message message:list){
            MessageVO vo=new MessageVO();
            BeanUtils.copyProperties(message,vo);
            result.add(vo);
        }

        return result;
    }

    private void deleteConversationListCache(Long userId){
        stringRedisTemplate.delete(RedisKeyConstants.CONVERSATION_LIST_KEY_PREFIX+userId);
    }
}
