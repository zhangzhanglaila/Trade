package com.trade.enums;

/**
 * 脚本执行状态枚举
 */
public enum ScriptStatus {
    /**
     * 等待执行
     */
    PENDING("等待执行"),
    
    /**
     * 运行中
     */
    RUNNING("运行中"),
    
    /**
     * 执行成功
     */
    SUCCESS("执行成功"),
    
    /**
     * 执行失败
     */
    FAILED("执行失败"),
    
    /**
     * 已取消
     */
    CANCELLED("已取消");
    
    private final String description;
    
    ScriptStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}

