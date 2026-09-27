package com.trade.model;

import com.trade.enums.ScriptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 脚本执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptExecutionResult {
    
    /**
     * 脚本名称
     */
    private String scriptName;
    
    /**
     * 执行状态
     */
    private ScriptStatus status;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 执行耗时（秒）
     */
    private Long duration;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 输出信息
     */
    private String output;
}

