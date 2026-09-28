package com.example.tdproject.generator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.tdproject.generator.domain.Ontology;
import com.example.tdproject.ontology.dto.*;
import com.example.tdproject.ontology.enums.RdfFileFormat;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 本体服务接口，定义本体管理相关操作
 */
public interface OntologyService extends IService<Ontology> {
    /**
     * 分页查询当前版本列表（仅查询状态为"当前版本"的本体）
     */
    IPage<Ontology> selectPage(Integer pageNum, Integer pageSize);

    /**
     * 根据项目名称查询所有版本（包含历史版本）
     */
    List<Ontology> getVersionsByProjectName(String projectName);

    /**
     * 检查项目名称是否已存在（仅检查当前版本）
     * @param projectName 项目名称
     * @param excludeId 排除的ID（用于更新时排除自身）
     * @return 是否存在
     */
    boolean checkNameExists(String projectName, Long excludeId);

    /**
     * 获取项目的当前版本
     */
    Ontology getCurrentVersion(String projectName);

    /**
     * 版本回滚（将历史版本设为当前版本）
     */
    boolean rollbackVersion(Long versionId);

    /**
     * 获取项目的历史版本列表
     */
    List<Ontology> getHistoryVersions(String projectName);

    /**
     * 按创建人分页查询当前版本本体
     */
    IPage<Ontology> searchByCreator(Integer pageNum, Integer pageSize, String creator);

    /**
     * 按项目名模糊匹配分页查询当前版本本体
     */
    IPage<Ontology> searchByProjectName(Integer pageNum, Integer pageSize, String projectName);

    /**
     * 从Excel导入本体（初始版本，项目不存在时新增）
     */
    boolean importOntology(MultipartFile excelFile, String projectName, String creator);

    /**
     * 从Excel导入并更新本体（项目存在时创建新版本）
     */
    boolean importAndUpdateOntology(MultipartFile excelFile, String projectName, String creator);

    /**
     * 从CSV导入本体（转换为RDF后导入）
     * @param csvFile CSV文件
     * @param projectName 项目名称
     * @param creator 创建人
     * @param csvMode CSV转换模式（original-原版本硬编码结构, custom-自定义配置）
     * @return 是否成功
     */
    boolean importOntologyFromCsv(MultipartFile csvFile, String projectName, String creator, String csvMode);

    /**
     * 验证 RDF 文件格式
     * @param rdfFile RDF文件
     * @return 验证结果
     */
    RdfValidationResult validateRdfFormat(MultipartFile rdfFile);

    /**
     * 从RDF文件导入本体（直接导入到图数据库）
     * @param rdfFile RDF/OWL/TTL/NT文件
     * @param projectName 项目名称
     * @param creator 创建人
     * @param fileFormat 文件格式（可选，自动识别）
     * @return 是否成功
     */
    boolean importOntologyFromRdf(MultipartFile rdfFile, String projectName, String creator, String fileFormat);

    /**
     * 按ID导出本体为Excel文件
     * @param id 本体ID
     * @param response HTTP响应对象，用于返回文件
     */
    void exportById(Long id, HttpServletResponse response) throws IOException;

    /**
     * 导出所有当前版本本体为Excel文件
     * @param response HTTP响应对象，用于返回文件
     */
    void exportAll(HttpServletResponse response) throws IOException;

    /**
     * 手动切换版本（将指定历史版本设为当前版本）
     */
    boolean switchVersion(Long targetVersionId);

    /**
     * 批量将项目所有版本标记为历史版本
     */
    boolean markAllAsHistory(String projectName);

    // ==================== 图数据库相关新方法 ====================

    /**
     * 创建本体并解析上传的RDF/OWL文件到图数据库
     * @param ontologyName 本体名称（对应projectName）
     * @param creatorName 创建人（对应creator）
     * @param version 版本号（对应versionNumber）
     * @param namespaceUri 命名空间URI
     * @param fileFormat 文件格式（OWL/RDF/TTL/NT）
     * @param file 上传的文件
     * @return 创建后的本体对象
     */
    Ontology createOntologyWithFile(String ontologyName, String creatorName, String version, 
                                    String namespaceUri, String fileFormat, MultipartFile file);

    /**
     * 检查本体名称在图数据库中是否存在
     * @param ontologyName 本体名称
     * @return 检查结果
     */
    CheckNameResult checkOntologyNameInGraph(String ontologyName);

    /**
     * 根据本体名称获取版本历史（用于入库弹窗）
     * @param ontologyName 本体名称
     * @return 版本信息列表（按时间倒序）
     */
    List<VersionInfo> getOntologyVersionHistory(String ontologyName);

    /**
     * 将本体导入图数据库（入库操作）
     * @param request 入库请求
     * @return 是否成功
     */
    boolean importOntologyToHouse(ImportToHouseRequest request);

    /**
     * 按版本号回滚本体版本
     * @param request 回滚请求
     * @return 是否成功
     */
    boolean rollbackOntologyByVersion(RollbackVersionRequest request);

    /**
     * 导出本体文件（OWL/RDF/TTL/NT格式）
     * @param id 本体ID
     * @param format 导出格式
     * @param response HTTP响应对象
     */
    void exportOntologyFile(Long id, RdfFileFormat format, HttpServletResponse response) throws IOException;

    // ==================== 本体详情查询方法 ====================

    /**
     * 获取本体统计数据
     * @param id 本体ID
     * @return 统计数据（类数量、实例数量、属性数量、三元组数量）
     */
    OntologyStats getOntologyStats(Long id);

    /**
     * 获取本体中的所有类
     * @param id 本体ID
     * @param keyword 搜索关键词（可选）
     * @return 类信息列表
     */
    List<ClassInfo> getOntologyClasses(Long id, String keyword);

    /**
     * 获取本体中的所有实例
     * @param id 本体ID
     * @param classUri 所属类URI（可选，为空则返回所有实例）
     * @param keyword 搜索关键词（可选，按名称过滤）
     * @return 实例信息列表
     */
    List<IndividualInfo> getOntologyIndividuals(Long id, String classUri, String keyword);

    /**
     * 获取本体中的所有属性
     * @param id 本体ID
     * @param propertyType 属性类型（object/datatype/annotation，为空则返回所有）
     * @param keyword 搜索关键词（可选）
     * @return 属性信息列表
     */
    List<PropertyInfo> getOntologyProperties(Long id, String propertyType, String keyword);
    
    // ==================== 类管理CRUD ====================
    
    /**
     * 创建类
     * @param ontologyId 本体ID
     * @param classInfo 类信息
     * @return 类的URI
     */
    String createOntologyClass(Long ontologyId, ClassInfo classInfo);
    
    /**
     * 更新类
     * @param ontologyId 本体ID
     * @param classUri 类URI
     * @param classInfo 类信息
     * @return 是否成功
     */
    boolean updateOntologyClass(Long ontologyId, String classUri, ClassInfo classInfo);
    
    /**
     * 删除类
     * @param ontologyId 本体ID
     * @param classUri 类URI
     * @return 是否成功
     */
    boolean deleteOntologyClass(Long ontologyId, String classUri);
    
    // ==================== 实例管理CRUD ====================
    
    /**
     * 创建实例
     * @param ontologyId 本体ID
     * @param individualInfo 实例信息
     * @return 实例的URI
     */
    String createOntologyIndividual(Long ontologyId, IndividualInfo individualInfo);
    
    /**
     * 更新实例
     * @param ontologyId 本体ID
     * @param individualUri 实例URI
     * @param individualInfo 实例信息
     * @return 是否成功
     */
    boolean updateOntologyIndividual(Long ontologyId, String individualUri, IndividualInfo individualInfo);
    
    /**
     * 删除实例
     * @param ontologyId 本体ID
     * @param individualUri 实例URI
     * @return 是否成功
     */
    boolean deleteOntologyIndividual(Long ontologyId, String individualUri);
    
    /**
     * 获取实例的关系（对象属性、数据属性、反向关系）
     * @param ontologyId 本体ID
     * @param individualUri 实例URI
     * @return 实例关系信息
     */
    IndividualRelations getIndividualRelations(Long ontologyId, String individualUri);
    
    // ==================== 属性管理CRUD ====================
    
    /**
     * 创建属性
     * @param ontologyId 本体ID
     * @param request 属性创建请求
     * @return 属性的URI
     */
    String createOntologyProperty(Long ontologyId, com.example.tdproject.ontology.dto.PropertyCreateRequest request);
    
    /**
     * 更新属性
     * @param ontologyId 本体ID
     * @param propertyUri 属性URI
     * @param request 属性更新请求
     * @return 是否成功
     */
    boolean updateOntologyProperty(Long ontologyId, String propertyUri, 
                                   com.example.tdproject.ontology.dto.PropertyCreateRequest request);
    
    /**
     * 删除属性
     * @param ontologyId 本体ID
     * @param propertyUri 属性URI
     * @return 是否成功
     */
    boolean deleteOntologyProperty(Long ontologyId, String propertyUri);
    
    /**
     * 获取本体可视化数据
     * @param ontologyId 本体ID
     * @return 可视化数据（节点和边）
     */
    OntologyVisualizationDTO getOntologyVisualization(Long ontologyId);

    /**
     * 解析某个本体实际可用的命名图 URI。
     *
     * <p>不能直接用当前版本的 URI：命名图 URI 是
     * {@code namedGraphPrefix + projectName + "/v" + versionNumber} 拼出来的
     * （见 JenaGraphRepositoryImpl.buildNamedGraphUri），而库里的 versionNumber
     * 种子值和前端输入框都自带 "v"（例如 "v1.0"），拼出来是 {@code /vv1.0}。
     * 一旦版本号写法与导入时不一致，直接用当前版本就会查到空图。
     *
     * <p>所以这里遍历该项目的所有版本，返回第一个确实存在数据的命名图；
     * 都不存在时退回当前版本的 URI（可能为空图）。
     *
     * @param ontologyId 本体ID
     * @return 命名图 URI；本体不存在或出错时返回 null
     */
    String resolveNamedGraphUri(Long ontologyId);

    /**
     * 图谱入库 - 保存编辑后的图谱数据
     * @param dto 入库请求数据
     * @return 入库结果
     */
    GraphWarehouseResponseDTO warehouseGraph(GraphWarehouseDTO dto);
}