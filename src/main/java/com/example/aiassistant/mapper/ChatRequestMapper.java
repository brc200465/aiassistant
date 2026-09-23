package com.example.aiassistant.mapper;

import com.example.aiassistant.entity.ChatRequest;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ChatRequestMapper {
    // 唯一键负责跨线程/跨实例去重；重复请求不覆盖已有数据。
    @Insert("""
    insert into chat_request (conversation_id,request_id,
        user_message_id,status,attempt,lease_until)
    values (#{conversationId},#{requestId},#{userMessageId},
        'PROCESSING',1,#{leaseUntil})""")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatRequest request);

    @Select("""
        select * from chat_request where conversation_id = #{conversationId}
        and request_id = #{requestId} for update
        """)
    ChatRequest lock(@Param("conversationId") Long conversationId, @Param("requestId") String requestId);

    @Update("""
        update chat_request set user_message_id = #{userMessageId},
        assistant_message_id = #{assistantMessageId}, status = #{status},
        error_message = #{errorMessage}, attempt = #{attempt}, lease_until = #{leaseUntil}
        where id = #{id}
        """)
    void update(ChatRequest request);

    @Select("""
        select *
        from chat_request
        where conversation_id=#{conversationId}
        and request_id<>#{requestId}
        and status='PROCESSING'
        order by id
        for update
        """)
    List<ChatRequest>lockOtherProcessing(
            @Param("conversationId") Long conversationId,
            @Param("requestId") String requestId);
}
