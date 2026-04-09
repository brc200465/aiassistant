package com.example.aiassistant.service.impl;

import com.example.aiassistant.common.ErrorCode;
import com.example.aiassistant.entity.Conversation;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.dto.ConversationCreateDTO;
import com.example.aiassistant.mapper.ConversationMapper;
import com.example.aiassistant.service.ConversationService;
import com.example.aiassistant.vo.ConversationVO;
import com.example.aiassistant.constant.RedisKeyConstants;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ConversationServiceImpl implements ConversationService{
    
    private static final long CONVERSATION_LIST_CACHE_TTL_MINUTES=10L;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    //开启新对话
    @Override
    public ConversationVO createConversation(Long userId,ConversationCreateDTO dto){
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

        deleteConversationListCache(userId);

        ConversationVO vo=new ConversationVO();
        BeanUtils.copyProperties(conversation,vo);
        return vo;
    }

    //获取会话列表
    @Override
    public List<ConversationVO>listConversations(Long userId){

        String redisKey=buildConversationListKey(userId);

        try{
            String cachedJson=stringRedisTemplate.opsForValue().get(redisKey);
            if(cachedJson!=null&&!cachedJson.isEmpty()){
                return objectMapper.readValue(cachedJson,new TypeReference<List<ConversationVO>>(){});
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        List<Conversation>conversationList=conversationMapper.findByUserId(userId);
        List<ConversationVO>result=new ArrayList<>();

        for(Conversation conversation:conversationList){
            ConversationVO vo=new ConversationVO();
            BeanUtils.copyProperties(conversation,vo);
            result.add(vo);
        }

        try{
            String json=objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(redisKey,json,CONVERSATION_LIST_CACHE_TTL_MINUTES,TimeUnit.MINUTES);
        }catch(Exception e){
            e.printStackTrace();
        }

        return result;
    }

    //获取会话详情
    @Override
    public ConversationVO getConversationDetail(Long userId,Long conversationId){
        Conversation conversation=conversationMapper.findById(conversationId);

        if(conversation==null)
            throw new BusinessException(ErrorCode.NOT_FOUND,"会话不存在");
        
        if(!conversation.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.NO_PERMISSION,"无权访问该会话");

        ConversationVO vo=new ConversationVO();
        BeanUtils.copyProperties(conversation,vo);
        return vo;
    }

    private String buildConversationListKey(Long userId){
        return RedisKeyConstants.CONVERSATION_LIST_KEY_PREFIX+userId;
    }

    private void deleteConversationListCache(Long userId){
        stringRedisTemplate.delete(buildConversationListKey(userId));
    }
}
