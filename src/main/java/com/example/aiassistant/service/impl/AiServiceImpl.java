package com.example.aiassistant.service.impl;

import com.example.aiassistant.config.AiProperties;
import com.example.aiassistant.dto.AiChatMessage;
import com.example.aiassistant.exception.BusinessException;
import com.example.aiassistant.service.AiService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiServiceImpl implements AiService{
    
    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient=HttpClient.newBuilder().
    connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String generateReply(List<AiChatMessage>messages){
        if(messages==null||messages.isEmpty()){
            throw new BusinessException("消息列表不能为空");
        }

        AiChatMessage lastUserMessage=findLastUserMessage(messages);
        String fallBackContent=lastUserMessage==null?"":lastUserMessage.getContent();


        if(!aiProperties.isEnabled()){
            return "这是AI的模拟回复：你刚才问的是 ->"+fallBackContent;
        }

        //配置校验
        if(isBlank(aiProperties.getBaseUrl())){
            throw new BusinessException("AI baseUrl 未配置");
        }

        if(isBlank(aiProperties.getApiKey())){
            throw new BusinessException("AI apiKey 未配置");
        }

        if(isBlank(aiProperties.getModel())){
            throw new BusinessException("AI model 未配置");
        }

        try{
            String url=aiProperties.getBaseUrl();
            if(url.endsWith("/")){
                url=url.substring(0,url.length()-1);
            }
            url=url+"/chat/completions";

            Map<String,Object>requestBody=new HashMap<>();
            requestBody.put("model",aiProperties.getModel());
            requestBody.put("messages",messages);

            String jsonBody=objectMapper.writeValueAsString(requestBody);

            HttpRequest request=HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(aiProperties.getTimeoutMillis()))
            .header("Content-type","application/json")
            .header("Authorization","Bearer "+aiProperties.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody,StandardCharsets.UTF_8))
            .build();

            HttpResponse<String>response=httpClient.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200||response.statusCode()>=300){
                throw new BusinessException("AI接口调用失败，HTTP状态码："+response.statusCode()+"，响应："+response.body());
            }

            JsonNode root=objectMapper.readTree(response.body());
            JsonNode choices=root.path("choices");

            if(!choices.isArray()||choices.isEmpty()){
                throw new BusinessException("AI接口返回异常：choices为空");
            }

            JsonNode firstChoice=choices.get(0);
            JsonNode messageNode=firstChoice.path("message");
            JsonNode contentNode=messageNode.path("content");

            String reply;

            if(contentNode.isString()){
                reply=contentNode.asString();
            }
            else if(contentNode.isArray()){
                StringBuilder sb=new StringBuilder();
                for(JsonNode part:contentNode){
                    if("text".equals(part.path("type").asString())){
                        sb.append(part.path("text").asString(""));
                    }
                }

                reply=sb.toString();
            }else{
                reply="";
            }
            if(isBlank(reply)){
                throw new BusinessException("AI接口返回异常：回复内容为空");
            }

            return reply.trim();
        }catch(BusinessException e){
            throw e;
        }catch(Exception e){
            e.printStackTrace();
            throw new BusinessException("调用AI服务失败："+e.getMessage());
        }
    }

    private AiChatMessage findLastUserMessage(List<AiChatMessage>messages){
        for(int i=messages.size()-1;i>=0;i--){
            AiChatMessage message=messages.get(i);
            if("user".equals(message.getRole()))
                return message;
        }
        return null;
    }

    private boolean isBlank(String str){
        return str==null||str.trim().isEmpty();
    }
}
