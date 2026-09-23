package com.example.aiassistant.service.impl;

import com.example.aiassistant.common.ErrorCode;
import com.example.aiassistant.constant.RedisKeyConstants;
import com.example.aiassistant.dto.AiChatMessage;
import com.example.aiassistant.dto.AiChatTurn;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.aiassistant.dto.StartResult;
import com.example.aiassistant.entity.ChatRequest;
import com.example.aiassistant.service.ChatRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import java.util.List;
import java.util.ArrayList;

@Service
public class ChatServiceImpl implements ChatService{

    private static final int CONTEXT_TURN_LIMIT=4;
    private static final Logger log=LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private AiService aiService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ChatRequestService chatRequestService;

    @Override
    public ChatResponseVO sendMessage(Long userId,ChatSendDTO dto){
        // 方法返回时，begin的事务已经提交
        StartResult start=chatRequestService.begin(userId,dto);

        if(!start.isShouldGenerate()){
            ChatResponseVO vo=start.getResponse();

            // 重复请求可能是在上次成功后、清理缓存前断开的
            if(ChatRequest.SUCCESS.equals(vo.getStatus())){
                deleteConversationListCache(userId);
            }

            return vo;
        }

        ChatRequest request=start.getRequest();
        ChatResponseVO vo;
        String failureMessage="回复生成失败，请重试";

        try{
            List<AiChatMessage>aiMessages=buildContext(request);

            String reply=aiService.generateReply(aiMessages);

            failureMessage="回复保存失败，请重试";

            // 独立短事务：保存回复并更新成功状态
            vo=chatRequestService.complete(
                    request.getConversationId(),request.getRequestId(),
                    request.getAttempt(),reply);
        }catch(RuntimeException e){
            log.error("处理问答失败，conversationId={},requestId={},attempt={}",
                    request.getConversationId(),request.getRequestId(),
                    request.getAttempt(),e);

            // complete抛出异常时，其事务已经结束，再开启失败记录事务
            vo=recordFailure(request,failureMessage);
        }

        // 缓存操作放在数据库事务之外
        if(ChatRequest.SUCCESS.equals(vo.getStatus())){
            deleteConversationListCache(userId);
        }

        return vo;
    }

    @Override
    public List<MessageVO>listMessages(Long userId,Long conversationId){
        Conversation conversation=conversationMapper.findById(conversationId);

        if(conversation==null){
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "会话不存在");
        }

        if(!conversation.getUserId().equals(userId)){
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    "无权访问该会话");
        }

        return messageMapper.findHistoryByConversationId(conversationId);
    }

    private List<AiChatMessage>buildContext(ChatRequest request){
        Message userMessage=messageMapper.findById(
                request.getUserMessageId());

        if(userMessage==null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "请求关联的用户消息不存在");
        }

        List<AiChatTurn>turns=messageMapper.findRecentSuccessfulTurns(
                request.getConversationId(),request.getUserMessageId(),
                CONTEXT_TURN_LIMIT);

        List<AiChatMessage>aiMessages=new ArrayList<>();

        for(AiChatTurn turn:turns){
            aiMessages.add(new AiChatMessage(
                    "user",turn.getUserContent()));

            aiMessages.add(new AiChatMessage(
                    "assistant",turn.getAssistantContent()));
        }

        aiMessages.add(new AiChatMessage(
                "user",userMessage.getContent()));

        return aiMessages;
    }

    private ChatResponseVO recordFailure(ChatRequest request,
                                         String errorMessage){
        try{
            return chatRequestService.fail(
                    request.getConversationId(),request.getRequestId(),
                    request.getAttempt(),errorMessage);
        }catch(RuntimeException e){
            log.error("记录问答失败状态失败，conversationId={},requestId={},attempt={}",
                    request.getConversationId(),request.getRequestId(),
                    request.getAttempt(),e);

            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "暂时无法确认处理结果，请稍后使用原请求ID重试");
        }
    }

    private void deleteConversationListCache(Long userId){
        try{
            stringRedisTemplate.delete(
                    RedisKeyConstants.CONVERSATION_LIST_KEY_PREFIX+userId);
        }catch(RuntimeException e){
            log.warn("删除会话列表缓存失败，userId={}",userId,e);
        }
    }
}
