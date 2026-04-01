package com.example.aiassistant.interceptor;

import com.example.aiassistant.common.Result;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor{

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    )throws Exception{
        Object loginUserId=request.getSession(false)==null?null:request.getSession(false).getAttribute("loginUserId");

        if(loginUserId!=null){
            return true;
        }

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error("请先登录")));
        return false;
    }
}
