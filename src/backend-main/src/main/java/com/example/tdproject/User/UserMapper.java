package com.example.tdproject.User;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 根据用户名查询用户
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND status = 1")
    User selectByUsername(String username);
    
    /**
     * 检查用户名是否已存在
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    int countByUsername(String username);
}
