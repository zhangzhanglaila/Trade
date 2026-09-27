package com.example.tdproject.User;

import lombok.Data;

/**
 * 用户资料更新DTO
 */
@Data
public class UserProfileDTO {
    
    private String nickname;
    
    private String email;
    
    private String phone;
    
    private String bio;
}
