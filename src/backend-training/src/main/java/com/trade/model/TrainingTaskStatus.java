package com.trade.model;

import com.trade.enums.ScriptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 训练任务状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingTaskStatus {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 总体状态
     */
    private ScriptStatus overallStatus;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 总耗时（秒）
     */
    private Long totalDuration;
    
    /**
     * 当前执行的脚本索引
     */
    private Integer currentStep;
    
    /**
     * 总步骤数
     */
    private Integer totalSteps;
    
    /**
     * 各脚本执行结果
     */
    private List<ScriptExecutionResult> scriptResults;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 进度百分比
     */
    private Double progress;
}

