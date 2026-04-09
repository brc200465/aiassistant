package com.example.aiassistant.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public Result(){}

    public Result(Integer code,String message,T data){
        this.code=code;
        this.message=message;
        this.data=data;
    }

    public static <T> Result<T> success(T data){
        return new Result<T>(ErrorCode.SUCCESS,"success",data);
    }

    public static Result<Void> success(){
        return new Result<>(ErrorCode.SUCCESS,"success",null);
    }

    public static Result<Void> error(Integer code,String message){
        return new Result<>(code,message,null);
    }

    public static Result<Void>error(String message){
        return new Result<>(ErrorCode.SYSTEM_ERROR,message,null);
    }
}
