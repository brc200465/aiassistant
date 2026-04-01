package com.example.aiassistant.mapper;

import com.example.aiassistant.entity.Conversation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMapper {
    
    @Insert("""
            insert into conversation(user_id,title,last_message_time,create_time,update_time)
            values(#{userId},#{title},#{lastMessageTime},now(),now())
            """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Conversation conversation);

    @Select("""
            select id,user_id,title,last_message_time,create_time,update_time
            from conversation
            where user_id=#{userId}
            order by last_message_time desc,id desc
            """)
    List<Conversation>findByUserId(Long userId);

    @Select("""
            select id,user_id,title,last_message_time,create_time,update_time
            from conversation
            where id=#{id}
            """)
    Conversation findById(Long id);
}
