package com.example.tdproject.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图数据库配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "ontology.graph")
public class OntologyGraphConfig {
    
    /**
     * 存储路径
     * 默认相对路径，相对于 Java 进程工作目录（项目根）解析；
     * 生产环境请通过 application.yaml 的 ontology.graph.store-path 或环境变量显式指定。
     */
    private String storePath = "runtime/ontology-graph-store";
    
    /**
     * 命名图URI前缀
     */
    private String namedGraphPrefix = "http://example.org/ontology/";
}