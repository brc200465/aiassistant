package com.example.aiassistant.mapper;

import com.example.aiassistant.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("""
            select id,username,password,nickname,create_time,update_time
            from user
            where username=#{username}
            """)
    User findByUsername(String username);

    @Select("""
            select id,username,password,nickname,create_time,update_time
            from user
            where id=#{id}
            """)
    User findById(Long id);

    @Insert("""
            insert into user (username,password,nickname,create_time,update_time)
            values(#{username},#{password},#{nickname},now(),now())
            """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(User user);
}
