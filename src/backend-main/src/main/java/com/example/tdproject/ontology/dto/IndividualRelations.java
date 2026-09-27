package com.example.tdproject.ontology.dto;

import lombok.Data;
import java.util.List;

/**
 * 实例关系查询结果DTO
 */
@Data
public class IndividualRelations {
    
    /**
     * 对象属性关系（该实例指向其他实例）
     */
    private List<ObjectPropertyRelation> objectProperties;
    
    /**
     * 数据属性（该实例的基本属性值）
     */
    private List<DatatypePropertyRelation> datatypeProperties;
    
    /**
     * 反向关系（其他实例指向该实例）
     */
    private List<InverseRelation> inverseRelations;
    
    /**
     * 对象属性关系内部类
     */
    @Data
    public static class ObjectPropertyRelation {
        /** 属性名 */
        private String propertyName;
        /** 属性URI */
        private String propertyUri;
        /** 目标实例名 */
        private String targetName;
        /** 目标实例URI */
        private String targetUri;
    }
    
    /**
     * 数据属性关系内部类
     */
    @Data
    public static class DatatypePropertyRelation {
        /** 属性名 */
        private String propertyName;
        /** 属性URI */
        private String propertyUri;
        /** 属性值 */
        private String value;
        /** 数据类型 */
        private String datatype;
    }
    
    /**
     * 反向关系内部类
     */
    @Data
    public static class InverseRelation {
        /** 源实例名 */
        private String sourceName;
        /** 源实例URI */
        private String sourceUri;
        /** 关系属性名 */
        private String propertyName;
        /** 关系属性URI */
        private String propertyUri;
    }
}
