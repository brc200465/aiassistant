package com.example.aiassistant.exception;

import com.example.aiassistant.common.ErrorCode;
import com.example.aiassistant.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler{
    @ExceptionHandler(BusinessException.class)
    public Result<Void>handleBusinessException(BusinessException e){
        return Result.error(e.getCode(),e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void>handleValidException(MethodArgumentNotValidException e){
        String message=e.getBindingResult().getFieldError()!=null?e.getBindingResult().getFieldError().getDefaultMessage():"参数校验失败";
        return Result.error(ErrorCode.PARAM_ERROR,message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void>handleException(Exception e){
        e.printStackTrace();
        return Result.error(ErrorCode.SYSTEM_ERROR,"服务器内部异常");
    }
}
