package com.example.tdproject.User;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户Service实现
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    
    @Override
    public User getByUsername(String username) {
        return baseMapper.selectByUsername(username);
    }
    
    @Override
    public boolean isUsernameAvailable(String username) {
        return baseMapper.countByUsername(username) == 0;
    }
    
    @Override
    public boolean updateUserProfile(Long userId, UserProfileDTO dto) {
        User user = new User();
        user.setId(userId);
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        // bio字段如需存储，需在User实体中添加
        return updateById(user);
    }
    
    @Override
    public boolean updateAvatar(Long userId, String avatarUrl) {
        User user = new User();
        user.setId(userId);
        user.setAvatar(avatarUrl);
        return updateById(user);
    }
}
