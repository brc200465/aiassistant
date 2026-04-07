package com.example.aiassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix="ai.openai")
public class AiProperties {
    /**
     * 是否启用真实AI
     */
   private boolean enabled=false;

   /**
    * 接口基础地址
    */
   private String baseUrl;

   /**
    * API Key
    */
   private String apiKey;

   /**
    * 模型名
    */
   private String model;

   /**
    * 超时时间（毫秒）
    */
   private int timeoutMillis=30000;
}
