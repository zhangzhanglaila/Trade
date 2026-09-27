package com.example.tdproject.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 本体配置类
 * 配置本体框架文件路径
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ontology")
public class OntologyConfig {
    
    /**
     * 本体框架文件路径（trade.rdf）
     * 用于CSV数据转换时参考本体结构
     *
     * 默认值为相对路径，相对于 Java 进程的工作目录（即项目根）解析；
     * 生产环境请通过 application.yaml 的 ontology.schema-path 或环境变量显式指定。
     */
    private String schemaPath = "deliverables/本体与映射规则/trade（protege建模导出）.rdf";
    
    /**
     * 默认命名空间
     */
    private String defaultNamespace = "http://example.org/ontology/";
    
    /**
     * 实例URI前缀
     */
    private String instancePrefix = "http://example.org/instance/";
}
