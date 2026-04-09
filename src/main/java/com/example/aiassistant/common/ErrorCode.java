package com.example.aiassistant.common;

public class ErrorCode {
    private ErrorCode(){}

    public static final int SUCCESS=1;

    //4xxx：业务/参数/权限类错误
    public static final int PARAM_ERROR=4001;
    public static final int NOT_LOGIN=4002;
    public static final int NO_PERMISSION=4003;
    public static final int NOT_FOUND=4004;
    public static final int DATA_CONFLICT=4005;

    //5xxx：系统/第三方调用类错误
    public static final int AI_CALL_ERROR=5001;
    public static final int SYSTEM_ERROR=5002;
}
