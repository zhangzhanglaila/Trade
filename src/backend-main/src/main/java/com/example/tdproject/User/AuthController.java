package com.example.tdproject.User;

import com.example.tdproject.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器（登录/注册）
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        // 查询用户（直接使用 Mapper）
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            return Result.error("用户名或密码错误");
        }
        
        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Result.error("用户名或密码错误");
        }
        
        // 检查状态
        if (user.getStatus() == 0) {
            return Result.error("账号已被禁用");
        }
        
        // 生成真正的 JWT Token
        String token = jwtTokenUtil.generateToken(user.getId());
        
        // 构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", convertToUserVO(user));
        
        return Result.success(data);
    }
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result register(@RequestBody RegisterRequest request) {
        // 检查用户名是否已存在
        if (userMapper.countByUsername(request.getUsername()) > 0) {
            return Result.error("用户名已存在");
        }
        
        // 创建用户（直接使用 Mapper）
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        
        boolean success = userMapper.insert(user) > 0;
        if (success) {
            return Result.success("注册成功");
        } else {
            return Result.error("注册失败");
        }
    }
    
    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result logout() {
        // TODO: 注销Token（如使用Redis存储Token）
        return Result.success("退出成功");
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public Result getUserInfo() {
        // TODO: 从SecurityContext获取当前用户ID
        // 临时返回模拟数据
        return Result.success(new HashMap<>());
    }
    
    /**
     * 检查用户名是否可用
     */
    @GetMapping("/check-username")
    public Result checkUsername(@RequestParam String username) {
        boolean available = userMapper.countByUsername(username) == 0;
        return Result.success(available);
    }
    
    /**
     * 转换为用户VO（去除敏感信息）
     */
    private Map<String, Object> convertToUserVO(User user) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", user.getId());
        vo.put("username", user.getUsername());
        vo.put("nickname", user.getNickname());
        vo.put("email", user.getEmail());
        vo.put("phone", user.getPhone());
        vo.put("avatar", user.getAvatar());
        return vo;
    }
}
