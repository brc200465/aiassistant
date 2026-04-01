package com.example.aiassistant.service;

import com.example.aiassistant.dto.LoginDTO;
import com.example.aiassistant.dto.RegisterDTO;
import com.example.aiassistant.vo.UserVO;
import jakarta.servlet.http.HttpSession;

public interface UserService {
    
    void register(RegisterDTO registerDTO);

    UserVO login(LoginDTO loginDTO,HttpSession session);

    void logout(HttpSession session);

    UserVO getCurrentUser(Long userId);
}
