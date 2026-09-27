package com.example.tdproject.ontology.repository;

import com.example.tdproject.ontology.dto.ClassInfo;
import com.example.tdproject.ontology.dto.IndividualInfo;
import com.example.tdproject.ontology.dto.OntologyVisualizationDTO;
import com.example.tdproject.ontology.dto.PropertyInfo;
import com.example.tdproject.ontology.enums.RdfFileFormat;
import org.apache.jena.rdf.model.Model;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 图数据库操作接口
 * 提供RDF数据的存储、查询、导出等功能
 */
public interface GraphRepository {
    
    /**
     * 检查命名图是否存在
     * @param namedGraphUri 命名图URI
     * @return 是否存在
     */
    boolean namedGraphExists(String namedGraphUri);
    
    /**
     * 获取所有命名图URI列表
     * @return 命名图URI列表
     */
    List<String> listNamedGraphs();
    
    /**
     * 获取匹配指定前缀的命名图URI列表
     * @param prefix 前缀
     * @return 命名图URI列表
     */
    List<String> listNamedGraphsByPrefix(String prefix);
    
    /**
     * 创建命名图（如果已存在则先删除）
     * @param namedGraphUri 命名图URI
     */
    void createNamedGraph(String namedGraphUri);
    
    /**
     * 删除命名图
     * @param namedGraphUri 命名图URI
     */
    void deleteNamedGraph(String namedGraphUri);
    
    /**
     * 从输入流加载RDF数据到命名图
     * @param namedGraphUri 命名图URI
     * @param inputStream 输入流
     * @param format 文件格式
     */
    void loadRdfToNamedGraph(String namedGraphUri, InputStream inputStream, RdfFileFormat format);
    
    /**
     * 将模型保存到命名图
     * @param namedGraphUri 命名图URI
     * @param model Jena模型
     */
    void saveModelToNamedGraph(String namedGraphUri, Model model);
    
    /**
     * 从命名图获取模型
     * @param namedGraphUri 命名图URI
     * @return Jena模型
     */
    Model getModelFromNamedGraph(String namedGraphUri);
    
    /**
     * 将命名图导出到输出流
     * @param namedGraphUri 命名图URI
     * @param outputStream 输出流
     * @param format 文件格式
     */
    void exportNamedGraph(String namedGraphUri, OutputStream outputStream, RdfFileFormat format);
    
    /**
     * 复制命名图
     * @param sourceGraphUri 源命名图URI
     * @param targetGraphUri 目标命名图URI
     */
    void copyNamedGraph(String sourceGraphUri, String targetGraphUri);
    
    /**
     * 获取命名图中的三元组数量
     * @param namedGraphUri 命名图URI
     * @return 三元组数量
     */
    long getTripleCount(String namedGraphUri);
    
    /**
     * 构建命名图URI
     * @param ontologyName 本体名称
     * @param version 版本号
     * @return 命名图URI
     */
    String buildNamedGraphUri(String ontologyName, String version);
    
    /**
     * 构建命名图URI（用于历史版本）
     * @param ontologyName 本体名称
     * @param version 版本号
     * @param timestamp 时间戳
     * @return 命名图URI
     */
    String buildNamedGraphUri(String ontologyName, String version, long timestamp);
    
    // ==================== 本体详情查询接口 ====================
    
    /**
     * 获取命名图中的所有类
     * @param namedGraphUri 命名图URI
     * @return 类信息列表
     */
    List<ClassInfo> getClasses(String namedGraphUri);
    
    /**
     * 获取命名图中的所有实例
     * @param namedGraphUri 命名图URI
     * @param classUri 所属类URI（可选，为空则返回所有实例）
     * @return 实例信息列表
     */
    List<IndividualInfo> getIndividuals(String namedGraphUri, String classUri);
    
    /**
     * 获取命名图中的所有属性
     * @param namedGraphUri 命名图URI
     * @param propertyType 属性类型（object/datatype/annotation，为空则返回所有）
     * @return 属性信息列表
     */
    List<PropertyInfo> getProperties(String namedGraphUri, String propertyType);
    
    /**
     * 获取指定类的实例数量
     * @param namedGraphUri 命名图URI
     * @param classUri 类URI
     * @return 实例数量
     */
    long getIndividualCountByClass(String namedGraphUri, String classUri);
    
    // ==================== 类/实例/属性的CRUD接口 ====================
    
    /**
     * 创建类
     * @param namedGraphUri 命名图URI
     * @param classInfo 类信息
     * @return 类的URI
     */
    String createClass(String namedGraphUri, ClassInfo classInfo);
    
    /**
     * 更新类
     * @param namedGraphUri 命名图URI
     * @param classUri 类URI
     * @param classInfo 类信息
     * @return 是否成功
     */
    boolean updateClass(String namedGraphUri, String classUri, ClassInfo classInfo);
    
    /**
     * 删除类
     * @param namedGraphUri 命名图URI
     * @param classUri 类URI
     * @return 是否成功
     */
    boolean deleteClass(String namedGraphUri, String classUri);
    
    // ==================== 实例CRUD接口 ====================
    
    /**
     * 创建实例
     * @param namedGraphUri 命名图URI
     * @param individualInfo 实例信息
     * @return 实例的URI
     */
    String createIndividual(String namedGraphUri, IndividualInfo individualInfo);
    
    /**
     * 更新实例
     * @param namedGraphUri 命名图URI
     * @param individualUri 实例URI
     * @param individualInfo 实例信息
     * @return 是否成功
     */
    boolean updateIndividual(String namedGraphUri, String individualUri, IndividualInfo individualInfo);
    
    /**
     * 删除实例
     * @param namedGraphUri 命名图URI
     * @param individualUri 实例URI
     * @return 是否成功
     */
    boolean deleteIndividual(String namedGraphUri, String individualUri);
    
    /**
     * 获取实例的关系（对象属性、数据属性、反向关系）
     * @param namedGraphUri 命名图URI
     * @param individualUri 实例URI
     * @return 实例关系信息
     */
    com.example.tdproject.ontology.dto.IndividualRelations getIndividualRelations(String namedGraphUri, String individualUri);
    
    // ==================== 属性CRUD接口 ====================
    
    /**
     * 创建属性
     * @param namedGraphUri 命名图URI
     * @param request 属性创建请求
     * @return 属性的URI
     */
    String createProperty(String namedGraphUri, com.example.tdproject.ontology.dto.PropertyCreateRequest request);
    
    /**
     * 更新属性
     * @param namedGraphUri 命名图URI
     * @param propertyUri 属性URI
     * @param request 属性更新请求
     * @return 是否成功
     */
    boolean updateProperty(String namedGraphUri, String propertyUri, 
                          com.example.tdproject.ontology.dto.PropertyCreateRequest request);
    
    /**
     * 删除属性
     * @param namedGraphUri 命名图URI
     * @param propertyUri 属性URI
     * @return 是否成功
     */
    boolean deleteProperty(String namedGraphUri, String propertyUri);
    
    /**
     * 获取可视化数据
     * @param namedGraphUri 命名图URI
     * @return 可视化DTO（包含节点和边）
     */
    OntologyVisualizationDTO getVisualizationData(String namedGraphUri);
    
    /**
     * 保存可视化数据到命名图
     * @param namedGraphUri 命名图URI
     * @param nodes 节点列表
     * @param edges 边列表
     */
    void saveVisualizationData(String namedGraphUri, 
                               List<OntologyVisualizationDTO.NodeDTO> nodes, 
                               List<OntologyVisualizationDTO.EdgeDTO> edges);
    
    /**
     * 获取Jena Dataset（用于图分析）
     * @return Dataset
     */
    org.apache.jena.query.Dataset getDataset();
}