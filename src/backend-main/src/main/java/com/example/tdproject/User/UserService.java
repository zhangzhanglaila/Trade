package com.example.tdproject.User;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户Service接口
 */
public interface UserService extends IService<User> {
    
    /**
     * 根据用户名查询用户
     */
    User getByUsername(String username);
    
    /**
     * 检查用户名是否可用
     */
    boolean isUsernameAvailable(String username);
    
    /**
     * 更新用户信息
     */
    boolean updateUserProfile(Long userId, UserProfileDTO dto);
    
    /**
     * 更新头像
     */
    boolean updateAvatar(Long userId, String avatarUrl);
}
