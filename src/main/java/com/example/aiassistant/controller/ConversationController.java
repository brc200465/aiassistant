package com.example.aiassistant.controller;

import com.example.aiassistant.common.ErrorCode;
import com.example.aiassistant.common.Result;
import com.example.aiassistant.dto.ConversationCreateDTO;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.service.ConversationService;
import com.example.aiassistant.vo.ConversationVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/conversations")
public class ConversationController {
    
    @Autowired
    private ConversationService conversationService; 

    @PostMapping
    public Result<ConversationVO>createConversation(@Valid @RequestBody ConversationCreateDTO dto,
        HttpSession session
    ){
        Long userId=getLoginUserId(session);
        ConversationVO vo=conversationService.createConversation(userId,dto);
        return Result.success(vo);
    }

    @GetMapping
    public Result<List<ConversationVO>>listConversations(HttpSession session){
        Long userId=getLoginUserId(session);
        List<ConversationVO>list=conversationService.listConversations(userId);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<ConversationVO>getConversationDetail(@PathVariable Long id,HttpSession session){
        Long userId=getLoginUserId(session);
        ConversationVO vo=conversationService.getConversationDetail(userId,id);
        return Result.success(vo);
    }

    private Long getLoginUserId(HttpSession session){
        Object value=session.getAttribute("loginUserId");

        if(value==null)
            throw new BusinessException(ErrorCode.NOT_LOGIN,"请先登录");
        return (Long)value;
    }

}
