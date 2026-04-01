package com.example.aiassistant.controller;

import com.example.aiassistant.common.Result;
import com.example.aiassistant.dto.ChatSendDTO;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.service.ChatService;
import com.example.aiassistant.vo.ChatResponseVO;
import com.example.aiassistant.vo.MessageVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChatController {
    
    @Autowired
    private ChatService chatService;
    
    @PostMapping("/chat")
    public Result<ChatResponseVO>chat(@Valid @RequestBody ChatSendDTO dto,
        HttpSession session
    ){
        Long userId=(Long)getLoginUserId(session);
        ChatResponseVO vo=chatService.sendMessage(userId,dto);
        return Result.success(vo);
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>>listMessages(@PathVariable Long id,
        HttpSession session
    ){
        Long userId=getLoginUserId(session);
        List<MessageVO>list=chatService.listMessages(userId,id);
        return Result.success(list);
    }

    private Long getLoginUserId(HttpSession session){
        Long value=(Long)session.getAttribute("loginUserId");
        if(value==null)
            throw new BusinessException("请先登录");
        return value;
    }
}
