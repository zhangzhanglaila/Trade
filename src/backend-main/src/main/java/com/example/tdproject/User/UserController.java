package com.example.tdproject.User;

import com.example.tdproject.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器（用户信息管理）
 */
@RestController
@RequestMapping("/user")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Autowired
    private HttpServletRequest request;
    
    /**
     * 更新用户资料
     */
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody UserProfileDTO dto) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        
        boolean success = userService.updateUserProfile(userId, dto);
        if (success) {
            return Result.success("更新成功");
        } else {
            return Result.error("更新失败");
        }
    }
    
    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public Result uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        
        // TODO: 实现文件上传逻辑（保存到本地或OSS）
        // 临时返回模拟URL
        String avatarUrl = "/uploads/avatar/" + userId + ".jpg";
        
        boolean success = userService.updateAvatar(userId, avatarUrl);
        if (success) {
            Map<String, String> data = new HashMap<>();
            data.put("avatarUrl", avatarUrl);
            return Result.success(data);
        } else {
            return Result.error("上传失败");
        }
    }
    
    /**
     * 修改密码
     */
    @PutMapping("/password")
    public Result changePassword(@RequestBody Map<String, String> params) {
        try {
            // 从 Token 获取当前用户ID
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("未登录或登录已过期");
            }
            
            String oldPassword = params.get("oldPassword");
            String newPassword = params.get("newPassword");
            
            // 参数校验
            if (oldPassword == null || newPassword == null) {
                return Result.error("密码不能为空");
            }
            
            // 查询用户
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            // 验证旧密码
            String storedPassword = user.getPassword();
            if (storedPassword == null || !passwordEncoder.matches(oldPassword, storedPassword)) {
                return Result.error("原密码错误");
            }
            
            // 更新密码
            user.setPassword(passwordEncoder.encode(newPassword));
            int result = userMapper.updateById(user);
            
            if (result > 0) {
                return Result.success("密码修改成功");
            } else {
                return Result.error("密码修改失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("系统错误：" + e.getMessage());
        }
    }
    
    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats")
    public Result getUserStats() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        
        // TODO: 查询真实的统计数据
        Map<String, Object> stats = new HashMap<>();
        stats.put("ontologyCount", 5);
        stats.put("graphCount", 12);
        stats.put("queryCount", 128);
        
        return Result.success(stats);
    }
    
    /**
     * 获取当前用户ID（从Token中解析）
     */
    private Long getCurrentUserId() {
        // 从请求头中获取 token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        
        String token = authHeader.substring(7);
        return jwtTokenUtil.getUserIdFromToken(token);
    }
}
