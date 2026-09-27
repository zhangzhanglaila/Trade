package com.trade.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

/**
 * Python 脚本配置属性
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "python")
public class PythonScriptProperties {
    
    /**
     * Python 解释器路径
     */
    private String interpreter = "python";
    
    /**
     * 脚本配置
     */
    private Script script = new Script();
    
    /**
     * 数据路径配置
     */
    private DataConfig data = new DataConfig();
    
    /**
     * 模型输出路径配置
     */
    private ModelConfig model = new ModelConfig();

    /**
     * Flask 服务配置
     */
    private FlaskConfig flask = new FlaskConfig();
    
    /**
     * 配置加载后验证
     */
    @PostConstruct
    public void validate() {
        log.info("=== Python 脚本配置加载 ===");
        log.info("Python 解释器: {}", interpreter);
        log.info("脚本基础路径: {}", script != null ? script.getBasePath() : "null");
        log.info("进口 CSV 路径: {}", data != null && data.getImportData() != null ? data.getImportData().getCsvPath() : "null");
        log.info("出口 CSV 路径: {}", data != null && data.getExportData() != null ? data.getExportData().getCsvPath() : "null");
        log.info("进口模型输出路径: {}", model != null && model.getImportModel() != null ? model.getImportModel().getOutputPath() : "null");
        log.info("出口模型输出路径: {}", model != null && model.getExportModel() != null ? model.getExportModel().getOutputPath() : "null");
        log.info("Flask 服务启用: {}", flask != null && flask.isEnabled());
        log.info("Flask 服务端口: {}", flask != null ? flask.getPort() : "null");
        log.info("========================");
    }
    
    @Data
    public static class Script {
        /**
         * 脚本基础路径
         */
        private String basePath = "../python-scripts";
        
        /**
         * 数量训练脚本文件名
         */
        private String quantityTrain = "Trade_Transformer_LSTM_quantity.py";
        
        /**
         * 单价训练脚本文件名
         */
        private String priceTrain = "Trade_Transformer_LSTM_price.py";
        
        /**
         * API 部署脚本文件名
         */
        private String apiDeploy = "app.py";
    }
    
    @Data
    public static class DataConfig {
        /**
         * 进口数据配置
         */
        private ImportData importData = new ImportData();
        
        /**
         * 出口数据配置
         */
        private ExportData exportData = new ExportData();
        
        @Data
        public static class ImportData {
            /**
             * 进口 CSV 文件路径
             */
            private String csvPath;
        }
        
        @Data
        public static class ExportData {
            /**
             * 出口 CSV 文件路径
             */
            private String csvPath;
        }
    }
    
    @Data
    public static class ModelConfig {
        /**
         * 进口模型输出路径
         */
        private ImportModel importModel = new ImportModel();
        
        /**
         * 出口模型输出路径
         */
        private ExportModel exportModel = new ExportModel();
        
        @Data
        public static class ImportModel {
            /**
             * 进口模型输出目录
             */
            private String outputPath;
        }
        
        @Data
        public static class ExportModel {
            /**
             * 出口模型输出目录
             */
            private String outputPath;
        }
    }

    @Data
    public static class FlaskConfig {
        /**
         * 是否启用自动启动
         */
        private boolean enabled = true;

        /**
         * Flask 绑定主机
         */
        private String host = "0.0.0.0";

        /**
         * Flask 监听端口
         */
        private int port = 5000;

        /**
         * 健康检查路径
         */
        private String readinessPath = "/health";

        /**
         * 启动超时时间（秒）
         */
        private int startupTimeoutSeconds = 60;
    }
}

