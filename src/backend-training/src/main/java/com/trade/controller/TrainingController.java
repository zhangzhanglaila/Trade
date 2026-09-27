package com.trade.controller;

import com.trade.model.TrainingTaskStatus;
import com.trade.service.TrainingTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/training")
public class TrainingController {
    
    @Autowired
    private TrainingTaskService trainingTaskService;
    
    /**
     * 启动训练任务
     * 
     * @return 任务ID
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startTraining() {
        log.info("收到训练任务启动请求");
        
        try {
            String taskId = trainingTaskService.startTrainingTask();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("message", "训练任务已启动");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动训练任务失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "启动训练任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<TrainingTaskStatus> getTaskStatus(@PathVariable String taskId) {
        log.info("查询任务状态: {}", taskId);
        
        TrainingTaskStatus status = trainingTaskService.getTaskStatus(taskId);
        
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * 获取所有任务状态
     * 
     * @return 所有任务状态列表
     */
    @GetMapping("/status")
    public ResponseEntity<List<TrainingTaskStatus>> getAllTaskStatus() {
        log.info("查询所有任务状态");
        
        List<TrainingTaskStatus> statusList = trainingTaskService.getAllTaskStatus();
        return ResponseEntity.ok(statusList);
    }
}

