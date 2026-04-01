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
        return new Result<T>(1,"success",data);
    }

    public static <T>Result<T> success(){
        return new Result<T>(1,"success",null);
    }

    public static <T>Result<T> error(String message){
        return new Result<T>(0,message,null);
    }
}
