package com.trade.service;

import com.trade.config.PythonScriptProperties;
import com.trade.enums.ScriptStatus;
import com.trade.model.ScriptExecutionResult;
import com.trade.model.TrainingTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 训练任务服务
 */
@Slf4j
@Service
public class TrainingTaskService {
    
    @Autowired
    private PythonScriptService pythonScriptService;
    
    @Autowired
    private PythonScriptProperties properties;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final ConcurrentHashMap<String, TrainingTaskStatus> taskStatusMap = new ConcurrentHashMap<>();
    
    /**
     * 启动训练任务
     *
     * @return 任务ID
     */
    public String startTrainingTask() {
        String taskId = UUID.randomUUID().toString();
        
        TrainingTaskStatus status = TrainingTaskStatus.builder()
                .taskId(taskId)
                .overallStatus(ScriptStatus.PENDING)
                .startTime(LocalDateTime.now())
                .currentStep(0)
                .totalSteps(6) // 进口数量、进口单价、出口数量、出口单价、API部署（可选）
                .scriptResults(new ArrayList<>())
                .progress(0.0)
                .build();
        
        taskStatusMap.put(taskId, status);
        
        // 异步执行训练任务
        CompletableFuture.runAsync(() -> executeTrainingTask(taskId), executorService);
        
        return taskId;
    }
    
    /**
     * 执行训练任务
     *
     * @param taskId 任务ID
     */
    private void executeTrainingTask(String taskId) {
        TrainingTaskStatus status = taskStatusMap.get(taskId);
        if (status == null) {
            log.error("任务不存在: {}", taskId);
            return;
        }
        
        status.setOverallStatus(ScriptStatus.RUNNING);
        List<ScriptExecutionResult> results = status.getScriptResults();
        
        try {
            // 步骤1: 训练进口数量模型
            log.info("开始执行步骤 1/6: 训练进口数量模型");
            status.setCurrentStep(1);
            status.setProgress(16.67);
            ScriptExecutionResult result1 = pythonScriptService.executeScript(
                    pythonScriptService.getScriptPath(properties.getScript().getQuantityTrain()),
                    pythonScriptService.buildQuantityTrainArgs("in")
            );
            results.add(result1);
            if (result1.getStatus() != ScriptStatus.SUCCESS) {
                throw new RuntimeException("进口数量模型训练失败: " + result1.getErrorMessage());
            }
            
            // 步骤2: 训练进口单价模型
            log.info("开始执行步骤 2/6: 训练进口单价模型");
            status.setCurrentStep(2);
            status.setProgress(33.33);
            ScriptExecutionResult result2 = pythonScriptService.executeScript(
                    pythonScriptService.getScriptPath(properties.getScript().getPriceTrain()),
                    pythonScriptService.buildPriceTrainArgs("in")
            );
            results.add(result2);
            if (result2.getStatus() != ScriptStatus.SUCCESS) {
                throw new RuntimeException("进口单价模型训练失败: " + result2.getErrorMessage());
            }
            
            // 步骤3: 训练出口数量模型
            log.info("开始执行步骤 3/6: 训练出口数量模型");
            status.setCurrentStep(3);
            status.setProgress(50.0);
            ScriptExecutionResult result3 = pythonScriptService.executeScript(
                    pythonScriptService.getScriptPath(properties.getScript().getQuantityTrain()),
                    pythonScriptService.buildQuantityTrainArgs("out")
            );
            results.add(result3);
            if (result3.getStatus() != ScriptStatus.SUCCESS) {
                throw new RuntimeException("出口数量模型训练失败: " + result3.getErrorMessage());
            }
            
            // 步骤4: 训练出口单价模型
            log.info("开始执行步骤 4/6: 训练出口单价模型");
            status.setCurrentStep(4);
            status.setProgress(66.67);
            ScriptExecutionResult result4 = pythonScriptService.executeScript(
                    pythonScriptService.getScriptPath(properties.getScript().getPriceTrain()),
                    pythonScriptService.buildPriceTrainArgs("out")
            );
            results.add(result4);
            if (result4.getStatus() != ScriptStatus.SUCCESS) {
                throw new RuntimeException("出口单价模型训练失败: " + result4.getErrorMessage());
            }
            
            // 步骤5: 启动 API 服务
            log.info("步骤 5/6: 自动部署 Flask API 服务");
            status.setCurrentStep(5);
            status.setProgress(83.33);
            ScriptExecutionResult result5 = pythonScriptService.startFlaskService();
            results.add(result5);
            if (result5.getStatus() != ScriptStatus.SUCCESS) {
                throw new RuntimeException("API 服务部署失败: " + result5.getErrorMessage());
            }
            
            // 步骤6: 完成
            status.setCurrentStep(6);
            status.setProgress(100.0);
            status.setOverallStatus(ScriptStatus.SUCCESS);
            status.setEndTime(LocalDateTime.now());
            long totalDuration = java.time.Duration.between(status.getStartTime(), status.getEndTime()).getSeconds();
            status.setTotalDuration(totalDuration);
            
            log.info("训练任务完成: {}, 总耗时: {} 秒", taskId, totalDuration);
            
        } catch (Exception e) {
            status.setOverallStatus(ScriptStatus.FAILED);
            status.setEndTime(LocalDateTime.now());
            status.setErrorMessage(e.getMessage());
            long totalDuration = java.time.Duration.between(status.getStartTime(), status.getEndTime()).getSeconds();
            status.setTotalDuration(totalDuration);
            log.error("训练任务失败: {}", taskId, e);
        }
    }
    
    /**
     * 获取任务状态
     *
     * @param taskId 任务ID
     * @return 任务状态
     */
    public TrainingTaskStatus getTaskStatus(String taskId) {
        return taskStatusMap.get(taskId);
    }
    
    /**
     * 获取所有任务状态
     *
     * @return 所有任务状态列表
     */
    public List<TrainingTaskStatus> getAllTaskStatus() {
        return new ArrayList<>(taskStatusMap.values());
    }
}

