package com.example.aiassistant.controller;

import com.example.aiassistant.common.ErrorCode;
import com.example.aiassistant.common.Result;
import com.example.aiassistant.dto.LoginDTO;
import com.example.aiassistant.dto.RegisterDTO;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.service.UserService;
import com.example.aiassistant.vo.UserVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<Void>register(@Valid @RequestBody RegisterDTO registerDTO){
        userService.register(registerDTO);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<UserVO>login(@Valid @RequestBody LoginDTO loginDTO,HttpSession session){
        UserVO userVO=userService.login(loginDTO,session);
        return Result.success(userVO);
    }

    @PostMapping("/logout")
    public Result<Void>logout(HttpSession session){
        userService.logout(session);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserVO>me(HttpSession session){
        Long userId=(Long)session.getAttribute("loginUserId");
        if(userId==null)
            throw new BusinessException(ErrorCode.NOT_LOGIN,"请先登录");

        UserVO userVO=userService.getCurrentUser(userId);
        return Result.success(userVO);
    }
}
