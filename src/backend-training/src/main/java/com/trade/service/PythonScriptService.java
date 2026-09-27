package com.trade.service;

import com.trade.config.PythonScriptProperties;
import com.trade.enums.ScriptStatus;
import com.trade.model.ScriptExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;

/**
 * Python 脚本执行服务
 */
@Slf4j
@Service
public class PythonScriptService {
    
    @Autowired
    private PythonScriptProperties properties;

    private final Object flaskProcessLock = new Object();
    private Process flaskProcess;
    
    /**
     * 执行 Python 脚本
     *
     * @param scriptPath 脚本路径
     * @param arguments  参数列表
     * @return 执行结果
     */
    public ScriptExecutionResult executeScript(String scriptPath, List<String> arguments) {
        ScriptExecutionResult result = ScriptExecutionResult.builder()
                .scriptName(new File(scriptPath).getName())
                .status(ScriptStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build();
        
        // 验证配置
        String interpreter = properties.getInterpreter();
        if (interpreter == null || interpreter.isEmpty()) {
            throw new IllegalStateException("Python 解释器路径未配置");
        }
        
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        command.add(interpreter);
        command.add(scriptPath);
        command.addAll(arguments);
        
        processBuilder.command(command);
        // 设置工作目录为脚本所在目录
        File scriptFile = new File(scriptPath);
        File parentDir = scriptFile.getParentFile();
        if (parentDir != null && parentDir.exists()) {
            processBuilder.directory(parentDir);
        }
        
        log.info("执行命令: {}", String.join(" ", command));
        
        Process process = null;
        try {
            process = processBuilder.start();
            
            // 创建 final 引用以便在 lambda 中使用
            final Process finalProcess = process;
            
            // 使用异步读取避免死锁（输出流缓冲区满导致进程阻塞）
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            // 异步读取标准输出
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(finalProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        log.info("[{}] {}", result.getScriptName(), line);
                    }
                } catch (Exception e) {
                    log.error("读取标准输出异常", e);
                }
            });
            
            // 异步读取错误输出
            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(finalProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                        log.error("[{}] ERROR: {}", result.getScriptName(), line);
                    }
                } catch (Exception e) {
                    log.error("读取错误输出异常", e);
                }
            });
            
            outputThread.start();
            errorThread.start();
            
            // 等待进程完成，设置超时时间为 24 小时
            boolean finished = process.waitFor(24, TimeUnit.HOURS);
            
            // 等待输出线程完成
            outputThread.join(5000); // 最多等待5秒
            errorThread.join(5000);
            
            result.setEndTime(LocalDateTime.now());
            long duration = java.time.Duration.between(result.getStartTime(), result.getEndTime()).getSeconds();
            result.setDuration(duration);
            
            if (!finished) {
                process.destroyForcibly();
                result.setStatus(ScriptStatus.FAILED);
                result.setErrorMessage("脚本执行超时（超过24小时）");
                log.error("脚本执行超时: {}", scriptPath);
            } else {
                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    result.setStatus(ScriptStatus.SUCCESS);
                    result.setOutput(output.toString());
                    log.info("脚本执行成功: {}, 耗时: {} 秒", scriptPath, duration);
                } else {
                    result.setStatus(ScriptStatus.FAILED);
                    result.setErrorMessage("脚本执行失败，退出码: " + exitCode);
                    result.setOutput(errorOutput.toString());
                    log.error("脚本执行失败: {}, 退出码: {}", scriptPath, exitCode);
                }
            }
            
        } catch (Exception e) {
            result.setEndTime(LocalDateTime.now());
            result.setStatus(ScriptStatus.FAILED);
            result.setErrorMessage("执行异常: " + e.getMessage());
            log.error("执行脚本异常: {}", scriptPath, e);
            
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        
        return result;
    }
    
    /**
     * 构建数量训练脚本参数
     *
     * @param tradeType 贸易类型 (in/out)
     * @return 参数列表
     */
    public List<String> buildQuantityTrainArgs(String tradeType) {
        List<String> args = new ArrayList<>();
        args.add("--csv_path");
        if ("in".equalsIgnoreCase(tradeType)) {
            String csvPath = properties.getData().getImportData().getCsvPath();
            String outputPath = properties.getModel().getImportModel().getOutputPath();
            if (csvPath == null || outputPath == null) {
                throw new IllegalStateException("配置未正确加载：进口数据路径或模型输出路径为空");
            }
            args.add(csvPath);
            args.add("--model_output_path");
            args.add(outputPath);
        } else {
            String csvPath = properties.getData().getExportData().getCsvPath();
            String outputPath = properties.getModel().getExportModel().getOutputPath();
            if (csvPath == null || outputPath == null) {
                throw new IllegalStateException("配置未正确加载：出口数据路径或模型输出路径为空");
            }
            args.add(csvPath);
            args.add("--model_output_path");
            args.add(outputPath);
        }
        args.add("--trade_type");
        args.add(tradeType);
        args.add("--target");
        args.add("quantity");
        return args;
    }
    
    /**
     * 构建单价训练脚本参数
     *
     * @param tradeType 贸易类型 (in/out)
     * @return 参数列表
     */
    public List<String> buildPriceTrainArgs(String tradeType) {
        List<String> args = new ArrayList<>();
        args.add("--csv_path");
        if ("in".equalsIgnoreCase(tradeType)) {
            String csvPath = properties.getData().getImportData().getCsvPath();
            String outputPath = properties.getModel().getImportModel().getOutputPath();
            if (csvPath == null || outputPath == null) {
                throw new IllegalStateException("配置未正确加载：进口数据路径或模型输出路径为空");
            }
            args.add(csvPath);
            args.add("--model_output_path");
            args.add(outputPath);
        } else {
            String csvPath = properties.getData().getExportData().getCsvPath();
            String outputPath = properties.getModel().getExportModel().getOutputPath();
            if (csvPath == null || outputPath == null) {
                throw new IllegalStateException("配置未正确加载：出口数据路径或模型输出路径为空");
            }
            args.add(csvPath);
            args.add("--model_output_path");
            args.add(outputPath);
        }
        args.add("--trade_type");
        args.add(tradeType);
        args.add("--target");
        args.add("price");
        return args;
    }
    
    /**
     * 启动 Flask 服务
     *
     * @return 执行结果
     */
    public ScriptExecutionResult startFlaskService() {
        ScriptExecutionResult result = ScriptExecutionResult.builder()
                .scriptName(properties.getScript().getApiDeploy())
                .status(ScriptStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build();

        PythonScriptProperties.FlaskConfig flaskConfig = properties.getFlask();
        if (flaskConfig == null || !flaskConfig.isEnabled()) {
            result.setStatus(ScriptStatus.SUCCESS);
            result.setOutput("Flask 自动部署已禁用，跳过启动");
            result.setEndTime(LocalDateTime.now());
            result.setDuration(java.time.Duration.between(result.getStartTime(), result.getEndTime()).getSeconds());
            return result;
        }

        String scriptPath = getScriptPath(properties.getScript().getApiDeploy());
        String interpreter = properties.getInterpreter();
        if (interpreter == null || interpreter.isEmpty()) {
            throw new IllegalStateException("Python 解释器路径未配置");
        }

        synchronized (flaskProcessLock) {
            if (flaskProcess != null && flaskProcess.isAlive()) {
                result.setStatus(ScriptStatus.SUCCESS);
                result.setOutput(String.format("Flask 服务已在端口 %d 运行", flaskConfig.getPort()));
                result.setEndTime(LocalDateTime.now());
                result.setDuration(java.time.Duration.between(result.getStartTime(), result.getEndTime()).getSeconds());
                return result;
            }

            try {
                List<String> command = new ArrayList<>();
                command.add(interpreter);
                command.add(scriptPath);
                command.add("--host");
                command.add(flaskConfig.getHost());
                command.add("--port");
                command.add(String.valueOf(flaskConfig.getPort()));

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                File scriptFile = new File(scriptPath);
                File parentDir = scriptFile.getParentFile();
                if (parentDir != null && parentDir.exists()) {
                    processBuilder.directory(parentDir);
                }

                log.info("启动 Flask 服务: {}", String.join(" ", command));
                Process process = processBuilder.start();
                attachStreamLogger(process, scriptFile.getName());
                flaskProcess = process;
            } catch (IOException e) {
                result.setStatus(ScriptStatus.FAILED);
                result.setErrorMessage("Flask 服务启动失败: " + e.getMessage());
                result.setEndTime(LocalDateTime.now());
                result.setDuration(java.time.Duration.between(result.getStartTime(), result.getEndTime()).getSeconds());
                log.error("启动 Flask 服务失败", e);
                return result;
            }
        }

        boolean ready = waitForFlaskReadiness(flaskConfig);
        result.setEndTime(LocalDateTime.now());
        result.setDuration(java.time.Duration.between(result.getStartTime(), result.getEndTime()).getSeconds());

        if (ready) {
            result.setStatus(ScriptStatus.SUCCESS);
            result.setOutput(String.format("Flask 服务已启动: http://localhost:%d", flaskConfig.getPort()));
            log.info("Flask 服务启动成功，端口: {}", flaskConfig.getPort());
        } else {
            result.setStatus(ScriptStatus.FAILED);
            result.setErrorMessage(String.format("Flask 服务在 %d 秒内未就绪", flaskConfig.getStartupTimeoutSeconds()));
            log.error("Flask 服务启动超时");
            stopFlaskProcess();
        }

        return result;
    }

    /**
     * 获取脚本完整路径
     *
     * @param scriptFileName 脚本文件名
     * @return 完整路径
     */
    public String getScriptPath(String scriptFileName) {
        String basePathStr = properties.getScript().getBasePath();
        if (basePathStr == null || basePathStr.isEmpty()) {
            throw new IllegalStateException("脚本基础路径未配置");
        }
        
        File basePath = new File(basePathStr);
        // 如果是相对路径，基于项目根目录
        if (!basePath.isAbsolute()) {
            String projectRoot = System.getProperty("user.dir");
            // 处理相对路径，如 ../python-scripts
            basePath = new File(projectRoot, basePathStr).getAbsoluteFile();
        }
        
        File scriptFile = new File(basePath, scriptFileName);
        String absolutePath = scriptFile.getAbsolutePath();
        
        // 验证文件是否存在
        if (!scriptFile.exists()) {
            log.warn("脚本文件不存在: {}", absolutePath);
        }
        
        return absolutePath;
    }

    private void attachStreamLogger(Process process, String scriptName) {
        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[{}] {}", scriptName, line);
                }
            } catch (Exception e) {
                log.error("读取 Flask 标准输出异常", e);
            }
        });
        stdoutThread.setDaemon(true);
        stdoutThread.start();

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.error("[{}] ERROR: {}", scriptName, line);
                }
            } catch (Exception e) {
                log.error("读取 Flask 错误输出异常", e);
            }
        });
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    private boolean waitForFlaskReadiness(PythonScriptProperties.FlaskConfig config) {
        String readinessPath = config.getReadinessPath();
        if (readinessPath == null || readinessPath.isEmpty()) {
            readinessPath = "/health";
        }
        String url = String.format("http://127.0.0.1:%d%s", config.getPort(),
                readinessPath.startsWith("/") ? readinessPath : "/" + readinessPath);
        long timeoutMillis = Math.max(5, config.getStartupTimeoutSeconds()) * 1000L;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMillis) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                connection.setRequestMethod("GET");
                int status = connection.getResponseCode();
                connection.disconnect();
                if (status >= 200 && status < 300) {
                    return true;
                }
            } catch (IOException e) {
                log.debug("Flask 健康检查未通过: {}", e.getMessage());
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    private void stopFlaskProcess() {
        synchronized (flaskProcessLock) {
            if (flaskProcess != null) {
                if (flaskProcess.isAlive()) {
                    flaskProcess.destroy();
                }
                flaskProcess = null;
            }
        }
    }

    @PreDestroy
    public void onDestroy() {
        stopFlaskProcess();
    }
}

