package com.example.aiassistant.mapper;

import com.example.aiassistant.entity.Message;
import com.example.aiassistant.vo.MessageVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import com.example.aiassistant.dto.AiChatTurn;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {

        @Select("select * from message where id = #{id}")
        Message findById(Long id);

        @Insert("""
                        insert into message (conversation_id,role,content,token_count,create_time)
                        values(#{conversationId},#{role},#{content},#{tokenCount},now())
                        """)
        @Options(useGeneratedKeys=true,keyProperty="id")
        int insert(Message message);

        @Select("""
        select m.id,m.role,m.content,m.create_time,
               r.request_id,r.status,r.error_message
        from message m
        left join chat_request r
          on r.conversation_id=m.conversation_id
         and (r.user_message_id=m.id or r.assistant_message_id=m.id)
        where m.conversation_id=#{conversationId}
        order by coalesce(r.user_message_id,m.id) asc,
                 case when m.role='user' then 0 else 1 end asc,
                 m.id asc
        """)
        List<MessageVO>findHistoryByConversationId(Long conversationId);

        @Select("""
        select user_message_id,user_content,assistant_content
        from (
            select r.user_message_id,
                   u.content as user_content,
                   a.content as assistant_content
            from chat_request r
            inner join message u
              on u.id=r.user_message_id
             and u.conversation_id=r.conversation_id
             and u.role='user'
            inner join message a
              on a.id=r.assistant_message_id
             and a.conversation_id=r.conversation_id
             and a.role='assistant'
            where r.conversation_id=#{conversationId}
              and r.status='SUCCESS'
              and r.user_message_id<#{beforeUserMessageId}
            order by r.user_message_id desc
            limit #{limit}
        ) recent
        order by user_message_id asc
        """)
        List<AiChatTurn>findRecentSuccessfulTurns(
                @Param("conversationId") Long conversationId,
                @Param("beforeUserMessageId") Long beforeUserMessageId,
                @Param("limit") int limit);
}
