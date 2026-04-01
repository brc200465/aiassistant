package com.example.aiassistant.service.impl;

import com.example.aiassistant.entity.Conversation;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.dto.ConversationCreateDTO;
import com.example.aiassistant.mapper.ConversationMapper;
import com.example.aiassistant.service.ConversationService;
import com.example.aiassistant.vo.ConversationVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService{
    
    @Autowired
    private ConversationMapper conversationMapper;

    @Override
    public ConversationVO createConversation(Long userId,ConversationCreateDTO dto){
     if(userId==null)
            throw new BusinessException("请先登录");

    Conversation conversation=new Conversation();
    conversation.setUserId(userId);

    String title=dto.getTitle();
    if(title==null||title.trim().isEmpty()){
        conversation.setTitle("新会话");
    }else{
        conversation.setTitle(title.trim());
    }

    conversation.setLastMessageTime(LocalDateTime.now());
    
    conversationMapper.insert(conversation);
    ConversationVO vo=new ConversationVO();
    BeanUtils.copyProperties(conversation,vo);
    return vo;}

    @Override
    public List<ConversationVO>listConversations(Long userId){
        if(userId==null){
            throw new BusinessException("请先登录");
        }

        List<Conversation>conversationList=conversationMapper.findByUserId(userId);
        List<ConversationVO>result=new ArrayList<>();

        for(Conversation conversation:conversationList){
            ConversationVO vo=new ConversationVO();
            BeanUtils.copyProperties(conversation,vo);
            result.add(vo);
        }

        return result;
    }

    @Override
    public ConversationVO getConversationDetail(Long userId,Long conversationId){
        if(userId==null)
            throw new BusinessException("请先登录");

    Conversation conversation=conversationMapper.findById(conversationId);
    if(conversation==null)
        throw new BusinessException("会话不存在");

    if(!conversation.getUserId().equals(userId)){
        throw new BusinessException("无权访问该会话");
    }

    ConversationVO vo=new ConversationVO();
    BeanUtils.copyProperties(conversation,vo);
    return vo;
}
}
