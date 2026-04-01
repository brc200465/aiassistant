package com.example.aiassistant.mapper;

import com.example.aiassistant.entity.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper {
    
    @Insert("""
            insert into message (conversation_id,role,content,token_count,create_time
            ) values(#{conversationId},#{role},#{content},#{tokenCount},now())
            """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Message message);

    @Select("""
            select id,conversation_id,role,content,token_count,create_time
            from message
            where conversation_id=#{conversationId}
            order by id asc
            """)
    List<Message>findByConversationId(Long conversationId);
}
