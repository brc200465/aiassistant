package com.example.aiassistant.service.impl;

import com.example.aiassistant.common.ErrorCode;
import com.example.aiassistant.config.AiProperties;
import com.example.aiassistant.dto.ChatSendDTO;
import com.example.aiassistant.dto.StartResult;
import com.example.aiassistant.entity.ChatRequest;
import com.example.aiassistant.entity.Conversation;
import com.example.aiassistant.entity.Message;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.mapper.ChatRequestMapper;
import com.example.aiassistant.mapper.ConversationMapper;
import com.example.aiassistant.mapper.MessageMapper;
import com.example.aiassistant.service.ChatRequestService;
import com.example.aiassistant.vo.ChatResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatRequestServiceImpl implements ChatRequestService{

    @Autowired
    private ChatRequestMapper chatRequestMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private AiProperties aiProperties;

    @Override
    @Transactional(rollbackFor=Exception.class)
    public StartResult begin(Long userId,ChatSendDTO dto){
        if(dto==null||dto.getConversationId()==null){
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "会话ID不能为空");
        }

        String requestId=dto.getRequestId();
        String content=dto.getContent();

        if(requestId==null||requestId.isBlank()||requestId.length()>64){
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "请求ID不能为空且长度不能超过64");
        }

        if(content==null||content.isBlank()||content.length()>2000){
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "消息不能为空且长度不能超过2000");
        }

        content=content.trim();

        // 锁定已有会话行，将请求检查和首次创建放在同一个短事务中
        Conversation conversation=conversationMapper.lockById(
                dto.getConversationId());

        if(conversation==null){
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "会话不存在");
        }

        if(!conversation.getUserId().equals(userId)){
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    "无权访问该会话");
        }

        ChatRequest request=chatRequestMapper.lock(
                dto.getConversationId(),requestId);

        if(request==null){
            checkConversationAvailable(dto.getConversationId(),requestId);

            Message userMessage=new Message();
            userMessage.setConversationId(dto.getConversationId());
            userMessage.setRole("user");
            userMessage.setContent(content);

            messageMapper.insert(userMessage);

            request=new ChatRequest();
            request.setConversationId(dto.getConversationId());
            request.setRequestId(requestId);
            request.setUserMessageId(userMessage.getId());
            request.setStatus(ChatRequest.PROCESSING);
            request.setAttempt(1);
            request.setLeaseUntil(buildLeaseUntil());

            chatRequestMapper.insert(request);

            return buildStartResult(request,true);
        }

        Message userMessage=messageMapper.findById(
                request.getUserMessageId());

        if(userMessage==null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "请求关联的用户消息不存在");
        }

        // 相同请求ID不能用于发送不同的问题
        if(!content.equals(userMessage.getContent())){
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "请求ID已用于其他内容，请使用新的请求ID");
        }

        if(ChatRequest.SUCCESS.equals(request.getStatus())){
            return buildStartResult(request,false);
        }

        boolean processing=ChatRequest.PROCESSING.equals(
                request.getStatus());

        if(processing&&request.getLeaseUntil()!=null
                &&request.getLeaseUntil().isAfter(LocalDateTime.now())){
            return buildStartResult(request,false);
        }

        if(!processing&&!ChatRequest.FAILED.equals(request.getStatus())){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "请求状态异常");
        }

        checkConversationAvailable(dto.getConversationId(),requestId);

        // FAILED或处理超时：复用原问题，开启下一次生成
        request.setStatus(ChatRequest.PROCESSING);
        request.setAttempt(request.getAttempt()+1);
        request.setErrorMessage(null);
        request.setLeaseUntil(buildLeaseUntil());

        chatRequestMapper.update(request);

        return buildStartResult(request,true);
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public ChatResponseVO complete(Long conversationId,String requestId,
                                   int attempt,String reply){

        ChatRequest request=lockRequest(conversationId,requestId);

        // 旧调用不能覆盖新一次重试，也不能重复保存回复
        if(!isCurrentAttempt(request,attempt)){
            return buildResponse(request);
        }

        if(reply==null||reply.isBlank()){
            throw new BusinessException(ErrorCode.AI_CALL_ERROR,
                    "AI回复不能为空");
        }

        Message assistantMessage=new Message();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(reply);

        messageMapper.insert(assistantMessage);

        request.setAssistantMessageId(assistantMessage.getId());
        request.setStatus(ChatRequest.SUCCESS);
        request.setErrorMessage(null);
        request.setLeaseUntil(null);

        chatRequestMapper.update(request);

        conversationMapper.updateLastMessageTime(conversationId);

        return buildResponse(request);
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public ChatResponseVO fail(Long conversationId,String requestId,
                               int attempt,String errorMessage){

        ChatRequest request=lockRequest(conversationId,requestId);

        if(!isCurrentAttempt(request,attempt)){
            return buildResponse(request);
        }

        if(errorMessage==null||errorMessage.isBlank()){
            errorMessage="回复生成失败，请重试";
        }

        // 调用方应传入安全提示，不直接传入异常详情
        if(errorMessage.length()>255){
            errorMessage=errorMessage.substring(0,255);
        }

        request.setStatus(ChatRequest.FAILED);
        request.setErrorMessage(errorMessage);
        request.setLeaseUntil(null);

        chatRequestMapper.update(request);

        return buildResponse(request);
    }

    private ChatRequest lockRequest(Long conversationId,String requestId){
        // 与begin保持一致的加锁顺序：先会话，再请求
        Conversation conversation=conversationMapper.lockById(
                conversationId);

        if(conversation==null){
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "会话不存在");
        }

        ChatRequest request=chatRequestMapper.lock(
                conversationId,requestId);

        if(request==null){
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "问答请求不存在");
        }

        return request;
    }

    private boolean isCurrentAttempt(ChatRequest request,int attempt){
        return ChatRequest.PROCESSING.equals(request.getStatus())
                &&request.getAttempt()==attempt;
    }

    private LocalDateTime buildLeaseUntil(){
        // 在AI请求超时之外预留一分钟，用于上下文查询和结果保存
        long timeoutMillis=Math.max(aiProperties.getTimeoutMillis(),1000);

        return LocalDateTime.now().plusNanos(
                (timeoutMillis+60000L)*1000000L);
    }

    private StartResult buildStartResult(ChatRequest request,
                                         boolean shouldGenerate){

        StartResult result=new StartResult();
        result.setShouldGenerate(shouldGenerate);
        result.setRequest(request);

        if(!shouldGenerate){
            result.setResponse(buildResponse(request));
        }

        return result;
    }

    private ChatResponseVO buildResponse(ChatRequest request){
        Message userMessage=messageMapper.findById(
                request.getUserMessageId());

        if(userMessage==null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "请求关联的用户消息不存在");
        }

        ChatResponseVO vo=new ChatResponseVO();
        vo.setConversationId(request.getConversationId());
        vo.setRequestId(request.getRequestId());
        vo.setStatus(request.getStatus());
        vo.setErrorMessage(request.getErrorMessage());
        vo.setUserMessageId(request.getUserMessageId());
        vo.setAssistantMessageId(request.getAssistantMessageId());
        vo.setUserMessage(userMessage.getContent());

        if(ChatRequest.SUCCESS.equals(request.getStatus())){
            if(request.getAssistantMessageId()==null){
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "成功请求缺少AI回复");
            }

            Message assistantMessage=messageMapper.findById(
                    request.getAssistantMessageId());

            if(assistantMessage==null){
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "请求关联的AI回复不存在");
            }

            vo.setAssistantMessage(assistantMessage.getContent());
        }

        return vo;
    }

    private void checkConversationAvailable(Long conversationId,
                                            String requestId){
        List<ChatRequest>requests=chatRequestMapper.lockOtherProcessing(
                conversationId,requestId);

        LocalDateTime now=LocalDateTime.now();

        // 只要还有一个未过期的任务，就拒绝新的生成任务
        for(ChatRequest request:requests){
            LocalDateTime leaseUntil=request.getLeaseUntil();

            if(leaseUntil!=null&&leaseUntil.isAfter(now)){
                throw new BusinessException(ErrorCode.CONVERSATION_BUSY,
                        "当前会话正在生成回复，请稍后再发送");
            }
        }

        // 其他任务均已过期，取消它们的提交资格
        for(ChatRequest request:requests){
            request.setStatus(ChatRequest.FAILED);
            request.setErrorMessage("处理超时，请重试");
            request.setLeaseUntil(null);

            chatRequestMapper.update(request);
        }
    }
}