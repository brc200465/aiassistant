package com.example.aiassistant.service.impl;

import com.example.aiassistant.dto.LoginDTO;
import com.example.aiassistant.dto.RegisterDTO;
import com.example.aiassistant.entity.User;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.mapper.UserMapper;
import com.example.aiassistant.service.UserService;
import com.example.aiassistant.vo.UserVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService{
    
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public void register(RegisterDTO registerDTO){
        String username=registerDTO.getUsername().trim();
        String password=registerDTO.getPassword();
        String nickname=registerDTO.getNickname();

        User existUser=userMapper.findByUsername(username);
        if(existUser!=null)
            throw new BusinessException("用户名已存在");

        User user=new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname((nickname==null||nickname.trim().isEmpty())?username:nickname.trim());

        userMapper.insert(user);
    }

    public UserVO login(LoginDTO loginDTO,HttpSession session){
        String username=loginDTO.getUsername().trim();
        String password=loginDTO.getPassword();
        
        User user=userMapper.findByUsername(username);
        if(user==null)
            throw new BusinessException("用户名或密码错误");

        if(!passwordEncoder.matches(password,user.getPassword())){
            throw new BusinessException("用户名或密码错误");
        }
        session.setAttribute("loginUserId",user.getId());

        UserVO userVO=new UserVO();
        BeanUtils.copyProperties(user,userVO);
        return userVO;
    }

    @Override
    public void logout(HttpSession session){
        session.invalidate();
    }

    @Override
    public UserVO getCurrentUser(Long userId){

        User user=userMapper.findById(userId);
        if(user==null)
            throw new BusinessException("用户不存在");

        UserVO userVO=new UserVO();
        BeanUtils.copyProperties(user,userVO);
        return userVO;
    }
}
