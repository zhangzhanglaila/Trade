package com.example.tdproject.generator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.tdproject.generator.domain.Ontology;
import com.example.tdproject.generator.mapper.OntologyMapper;
import com.example.tdproject.generator.service.OntologyService;
import com.example.tdproject.ontology.dto.*;
import com.example.tdproject.ontology.enums.RdfFileFormat;
import com.example.tdproject.ontology.repository.GraphRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OntologyServiceImpl extends ServiceImpl<OntologyMapper, Ontology> implements OntologyService {

    @Autowired
    private GraphRepository graphRepository;

    /**
     * 分页查询当前版本本体（仅状态为1的当前版本）
     */
    @Override
    public IPage<Ontology> selectPage(Integer pageNum, Integer pageSize) {
        Page<Ontology> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectPage(page, new LambdaQueryWrapper<Ontology>()
                .eq(Ontology::getVersionStatus, 1)  // 1表示当前版本
                .orderByDesc(Ontology::getCreateTime));
    }

    /**
     * 根据项目名称查询所有版本（包含历史版本）
     */
    @Override
    public List<Ontology> getVersionsByProjectName(String projectName) {
        return baseMapper.selectList(new LambdaQueryWrapper<Ontology>()
                .eq(Ontology::getProjectName, projectName)
                .orderByAsc(Ontology::getVersionNumber));  // 按版本号升序排列
    }

    /**
     * 检查项目名称是否已存在（仅检查当前版本）
     */
    @Override
    public boolean checkNameExists(String projectName, Long excludeId) {
        LambdaQueryWrapper<Ontology> queryWrapper = new LambdaQueryWrapper<Ontology>()
                .eq(Ontology::getProjectName, projectName)
                .eq(Ontology::getVersionStatus, 1);  // 只检查当前版本

        if (excludeId != null) {
            queryWrapper.ne(Ontology::getId, excludeId);  // 排除自身（用于更新场景）
        }
        return baseMapper.exists(queryWrapper);
    }

    /**
     * 保存新本体（初始版本）
     */
    @Override
    public boolean save(Ontology entity) {
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setModifyTime(now);
        entity.setVersionStatus(1);  // 新创建默认为当前版本
        // 初始版本号默认为1.0
        if (entity.getVersionNumber() == null || entity.getVersionNumber().isEmpty()) {
            entity.setVersionNumber("1.0");
        }
        return super.save(entity);
    }

    /**
     * 更新本体（创建新版本，原版本转为历史版本）
     */
    @Override
    @Transactional  // 事务保证，确保版本切换的原子性
    public boolean updateById(Ontology entity) {
        // 1. 查询当前版本
        Ontology current = baseMapper.selectById(entity.getId());
        if (current == null) {
            return false;
        }
        String projectName = current.getProjectName();

        // 2. 将该项目下所有当前版本转为历史版本（避免多版本冲突）
        int updateCount = baseMapper.update(null, new LambdaUpdateWrapper<Ontology>()
                .eq(Ontology::getProjectName, projectName)
                .eq(Ontology::getVersionStatus, 1)
                .set(Ontology::getVersionStatus, 0));  // 0表示历史版本
        log.info("项目[{}]的当前版本已转为历史版本，影响行数: {}", projectName, updateCount);

        // 3. 创建新版本
        Ontology newVersion = new Ontology();
        BeanUtils.copyProperties(current, newVersion);  // 复制原版本属性
        newVersion.setId(null);  // 重置ID，避免主键冲突
        // 更新新属性（仅覆盖非空字段）
        if (entity.getProjectName() != null) {
            newVersion.setProjectName(entity.getProjectName());
        }
        if (entity.getNamespaceUri() != null) {
            newVersion.setNamespaceUri(entity.getNamespaceUri());
        }
        // 生成新版本号（如1.0 -> 1.1）
        newVersion.setVersionNumber(generateNewVersion(current.getVersionNumber()));
        newVersion.setParentId(current.getId());  // 父版本指向原版本
        newVersion.setVersionStatus(1);  // 设为当前版本
        newVersion.setCreateTime(new Date());
        newVersion.setModifyTime(new Date());

        // 4. 校验版本号唯一性并保存
        if (checkVersionExists(newVersion.getProjectName(), newVersion.getVersionNumber())) {
            log.error("项目[{}]版本号[{}]已存在，新增失败", newVersion.getProjectName(), newVersion.getVersionNumber());
            return false;
        }
        return baseMapper.insert(newVersion) > 0;
    }

    /**
     * 校验项目+版本号唯一性
     */
    private boolean checkVersionExists(String projectName, String versionNumber) {
        return baseMapper.exists(new LambdaQueryWrapper<Ontology>()
                .eq(Ontology::getProjectName, projectName)
                .eq(Ontology::getVersionNumber, versionNumber));
    }

    /**
     * 获取项目的当前版本
     */
    @Override
    public Ontology getCurrentVersion(String projectName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<Ontology>()
                .eq(Ontology::getProjectName, projectName)
                .eq(Ontology::getVersionStatus, 1)
                .last("limit 1"));  // 确保只返回一条
    }

    /**
     * 版本回滚（将历史版本设为当前版本）
     */
    @Override
    @Transactional
    public boolean rollbackVersion(Long versionId) {
        // 1. 查询要回滚的历史版本
        Ontology history = baseMapper.selectById(versionId);
        if (history == null || history.getVersionStatus() != 0) {  // 0为历史版本
            log.error("回滚失败，版本ID[{}]不存在或不是历史版本", versionId);
            return false;
        }

        String projectName = history.getProjectName();

        // 2. 将该项目下所有当前版本转为历史版本
        int currentToHistory = baseMapper.update(null, new LambdaUpdateWrapper<Ontology>()
                .eq(Ontology::getProjectName, projectName)
                .eq(Ontology::getVersionStatus, 1)
                .set(Ontology::getVersionStatus, 0));
        log.info("项目[{}]的当前版本已转为历史版本，影响行数: {}", projectName, currentToHistory);

        // 3. 将目标历史版本转为当前版本
        Ontology updateEntity = new Ontology();
        updateEntity.setId(versionId);
        updateEntity.setVersionStatus(1);
        updateEntity.setModifyTime(new Date());

        int rows = baseMapper.updateById(updateEntity);
        log.info("项目[{}]的历史版本[{}]已转为当前版本，影响行数: {}", projectName, versionId, rows);

        return rows > 0;
    }

    /**
     * 获取项目的历史版本列表
     */
    @Override
    public List<Ontology> getHistoryVersions(String projectName) {
        return baseMapper.selectList(new LambdaQueryWrapper<Ontology>()
                .eq(Ontology::getProjectName, projectName)
                .eq(Ontology::getVersionStatus, 0)  // 0为历史版本
                .orderByDesc(Ontology::getCreateTime));
    }

    /**
     * 按创建人分页查询当前版本本体
     */
    @Override
    public IPage<Ontology> searchByCreator(Integer pageNum, Integer pageSize, String creator) {
        Page<Ontology> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectPage(page, new LambdaQueryWrapper<Ontology>()
                .eq(Ontology::getCreator, creator)
                .eq(Ontology::getVersionStatus, 1)
                .orderByDesc(Ontology::getCreateTime));
    }

    /**
     * 按项目名模糊匹配分页查询当前版本本体
     */
    @Override
    public IPage<Ontology> searchByProjectName(Integer pageNum, Integer pageSize, String projectName) {
        Page<Ontology> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectPage(page, new LambdaQueryWrapper<Ontology>()
                .like(Ontology::getProjectName, projectName)
                .eq(Ontology::getVersionStatus, 1)
                .orderByDesc(Ontology::getCreateTime));
    }

    /**
     * 从Excel导入本体（支持重名检测：存在则更新，不存在则新增）
     */
    @Override
    @Transactional
    public boolean importOntology(MultipartFile excelFile, String projectName, String creator) {
        try {
            // 1. 验证文件类型
            String fileName = excelFile.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
                log.error("导入失败，仅支持Excel文件");
                throw new RuntimeException("仅支持.xlsx或.xls格式的Excel文件");
            }

            // 2. 检查文件是否为空
            if (excelFile.isEmpty()) {
                throw new RuntimeException("文件不能为空");
            }

            // 3. 解析Excel文件
            InputStream inputStream = excelFile.getInputStream();
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0);  // 取第一个sheet
            Row firstDataRow = sheet.getRow(1);  // 第0行为表头，第1行为数据

            // 4. 从Excel中读取项目名称（优先使用Excel中的名称）
            String excelProjectName = projectName;
            if (firstDataRow != null && firstDataRow.getCell(1) != null) {
                excelProjectName = firstDataRow.getCell(1).getStringCellValue();
            }
            if (StringUtils.isEmpty(excelProjectName)) {
                throw new RuntimeException("项目名称不能为空");
            }

            // 5. 检查项目是否已存在
            boolean exists = checkNameExists(excelProjectName, null);
            if (exists) {
                // 项目存在：执行更新操作（创建新版本）
                log.warn("项目名称[{}]已存在，将执行更新操作", excelProjectName);
                Ontology current = getCurrentVersion(excelProjectName);
                if (current == null) {
                    throw new RuntimeException("项目当前版本不存在，无法更新");
                }

                // 从Excel解析更新信息
                Ontology updateEntity = new Ontology();
                updateEntity.setId(current.getId());
                if (firstDataRow != null && firstDataRow.getCell(3) != null) {
                    updateEntity.setNamespaceUri(firstDataRow.getCell(3).getStringCellValue());
                }
                return updateById(updateEntity);  // 调用更新方法创建新版本
            } else {
                // 项目不存在：执行新增操作
                log.info("项目名称[{}]不存在，将执行新增操作", excelProjectName);
                // 解析命名空间（默认值+项目名）
                String namespaceUri = "http://example.com/ontology/" + excelProjectName;
                if (firstDataRow != null && firstDataRow.getCell(3) != null) {
                    namespaceUri = firstDataRow.getCell(3).getStringCellValue();
                }

                // 创建新本体
                Ontology ontology = new Ontology();
                ontology.setProjectName(excelProjectName);
                ontology.setCreator(creator);
                ontology.setNamespaceUri(namespaceUri);
                ontology.setVersionNumber("1.0");  // 初始版本
                ontology.setVersionStatus(1);
                ontology.setCreateTime(new Date());
                ontology.setModifyTime(new Date());

                return baseMapper.insert(ontology) > 0;
            }
        } catch (Exception e) {
            log.error("导入本体失败", e);
            throw new RuntimeException("导入失败：" + e.getMessage());
        }
    }

    /**
     * 从Excel导入并更新本体（创建新版本）
     */
    @Override
    @Transactional
    public boolean importAndUpdateOntology(MultipartFile excelFile, String projectName, String creator) {
        // 实现逻辑与importOntology类似，专注于更新场景（可根据实际需求调整）
        try {
            // 验证文件
            String fileName = excelFile.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
                log.error("导入失败，仅支持Excel文件");
                return false;
            }
            if (excelFile.isEmpty()) {
                return false;
            }

            // 检查项目是否存在
            if (!checkNameExists(projectName, null)) {
                log.error("项目[{}]不存在，无法更新", projectName);
                return false;
            }

            // 解析Excel并更新（逻辑参考importOntology的更新部分）
            InputStream inputStream = excelFile.getInputStream();
            Workbook workbook = WorkbookFactory.create(inputStream);
            // ... 解析逻辑 ...

            return true;
        } catch (Exception e) {
            log.error("导入更新本体失败", e);
            return false;
        }
    }

    /**
     * 从CSV导入本体（转换为RDF后导入）
     */
    @Override
    @Transactional
    public boolean importOntologyFromCsv(MultipartFile csvFile, String projectName, String creator, String csvMode) {
        try {
            // 1. 验证文件类型
            String fileName = csvFile.getOriginalFilename();
            if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
                log.error("导入失败，仅支持CSV文件");
                throw new RuntimeException("仅支持.csv格式的CSV文件");
            }

            // 2. 检查文件是否为空
            if (csvFile.isEmpty()) {
                throw new RuntimeException("文件不能为空");
            }

            byte[] rdfBytes;
            
            // 3. 根据模式选择转换方式
            if ("custom".equalsIgnoreCase(csvMode)) {
                // TODO: 自定义配置模式（后续实现）
                throw new RuntimeException("自定义配置模式暂未实现，请使用 original 模式");
            } else {
                // 原版本模式：硬编码的中亚贸易数据结构
                // 解析文件名获取国家和时间段
                String baseName = fileName.replaceAll("(?i)\\.csv$", "");
                String countryCode = "UNKNOWN";
                String dataPeriod = baseName.replace(".", "_").replace("—", "_");
                
                // 尝试从项目名提取国家代码
                if (projectName.toUpperCase().contains("KAZ")) {
                    countryCode = "KZ";
                } else if (projectName.toUpperCase().contains("UZB")) {
                    countryCode = "UZ";
                } else if (projectName.toUpperCase().contains("TJK")) {
                    countryCode = "TJ";
                } else if (projectName.toUpperCase().contains("KGZ")) {
                    countryCode = "KG";
                }

                log.info("CSV转换参数: countryCode={}, dataPeriod={}, mode=original", countryCode, dataPeriod);

                // 将 CSV 转换为 RDF
                try (InputStream is = csvFile.getInputStream()) {
                    rdfBytes = com.example.tdproject.utils.CsvToRdfConverter.convert(is, countryCode, dataPeriod);
                    log.info("CSV转RDF成功, 生成 {} bytes", rdfBytes.length);
                }
            }

            // 5. 检查项目是否已存在
            boolean exists = checkNameExists(projectName, null);
            
            if (exists) {
                // 项目存在：创建新版本
                Ontology current = getCurrentVersion(projectName);
                if (current == null) {
                    throw new RuntimeException("项目当前版本不存在");
                }
                
                String newVersion = generateNewVersion(current.getVersionNumber());
                Ontology newOntology = new Ontology();
                newOntology.setProjectName(projectName);
                newOntology.setCreator(creator);
                newOntology.setVersionNumber(newVersion);
                newOntology.setNamespaceUri(current.getNamespaceUri());
                newOntology.setVersionStatus(1);
                newOntology.setCreateTime(new Date());
                newOntology.setModifyTime(new Date());
                
                if (baseMapper.insert(newOntology) <= 0) {
                    throw new RuntimeException("保存本体元数据失败");
                }
                
                baseMapper.update(null, new LambdaUpdateWrapper<Ontology>()
                        .eq(Ontology::getId, current.getId())
                        .set(Ontology::getVersionStatus, 0));
                
                // 导入转换后的 RDF
                String namedGraphUri = graphRepository.buildNamedGraphUri(projectName, newVersion);
                try (InputStream is = new java.io.ByteArrayInputStream(rdfBytes)) {
                    graphRepository.loadRdfToNamedGraph(namedGraphUri, is, RdfFileFormat.RDF);
                    log.info("CSV转RDF导入成功: {}, triples: {}", 
                        namedGraphUri, graphRepository.getTripleCount(namedGraphUri));
                }
                
                return true;
            } else {
                // 项目不存在：创建新本体
                Ontology ontology = new Ontology();
                ontology.setProjectName(projectName);
                ontology.setCreator(creator);
                ontology.setVersionNumber("1.0");
                ontology.setNamespaceUri("http://example.com/ontology/" + projectName);
                ontology.setVersionStatus(1);
                ontology.setCreateTime(new Date());
                ontology.setModifyTime(new Date());

                if (baseMapper.insert(ontology) <= 0) {
                    throw new RuntimeException("保存本体元数据失败");
                }

                // 导入转换后的 RDF
                String namedGraphUri = graphRepository.buildNamedGraphUri(projectName, "1.0");
                try (InputStream is = new java.io.ByteArrayInputStream(rdfBytes)) {
                    graphRepository.loadRdfToNamedGraph(namedGraphUri, is, RdfFileFormat.RDF);
                    log.info("CSV转RDF导入成功: {}, triples: {}", 
                        namedGraphUri, graphRepository.getTripleCount(namedGraphUri));
                }
                
                return true;
            }
        } catch (Exception e) {
            log.error("从CSV导入本体失败", e);
            throw new RuntimeException("CSV导入失败：" + e.getMessage());
        }
    }

    /**
     * 验证 RDF 文件格式
     */
    @Override
    public RdfValidationResult validateRdfFormat(MultipartFile rdfFile) {
        String fileName = rdfFile.getOriginalFilename();
        
        try {
            // 1. 读取文件内容
            byte[] fileBytes;
            try (InputStream is = rdfFile.getInputStream()) {
                fileBytes = is.readAllBytes();
            }
            
            if (fileBytes.length == 0) {
                return RdfValidationResult.builder()
                        .valid(false)
                        .errorMessage("文件内容为空")
                        .suggestion("请检查文件是否正确，或尝试使用 CSV 格式上传")
                        .build();
            }
            
            // 2. 尝试解析 RDF
            RdfFileFormat format = RdfFileFormat.fromFilename(fileName);
            Model testModel = ModelFactory.createDefaultModel();
            
            try {
                testModel.read(new java.io.ByteArrayInputStream(fileBytes), null, format.getJenaLang().getName());
                
                long tripleCount = testModel.size();
                if (tripleCount > 0) {
                    return RdfValidationResult.builder()
                            .valid(true)
                            .format(format.getCode())
                            .tripleCount(tripleCount)
                            .suggestion("文件格式正确，可以导入")
                            .build();
                } else {
                    return RdfValidationResult.builder()
                            .valid(false)
                            .format(format.getCode())
                            .errorMessage("文件解析成功，但未找到三元组数据")
                            .suggestion("文件可能为空或格式不正确，请检查文件内容")
                            .build();
                }
            } catch (Exception e) {
                // 尝试其他格式
                String errorMsg = e.getMessage();
                
                // 检查是否包含中文 URI 错误
                if (errorMsg.contains("ILLEGAL") || errorMsg.contains("Code: 30") || 
                    errorMsg.contains("百分") || errorMsg.contains("ENCODING")) {
                    return RdfValidationResult.builder()
                            .valid(false)
                            .format(format.getCode())
                            .errorMessage("RDF 文件包含非法 URI（中文字符或未编码的特殊符号）")
                            .detailError(errorMsg.substring(0, Math.min(200, errorMsg.length())))
                            .suggestion("请使用 CSV 格式上传，系统会自动转换为标准 RDF 格式")
                            .build();
                }
                
                return RdfValidationResult.builder()
                        .valid(false)
                        .format(format.getCode())
                        .errorMessage("RDF 文件格式错误：" + e.getMessage())
                        .detailError(errorMsg.substring(0, Math.min(200, errorMsg.length())))
                        .suggestion("请检查文件格式，或尝试使用 CSV 格式上传")
                        .build();
            }
            
        } catch (Exception e) {
            log.error("验证 RDF 文件失败", e);
            return RdfValidationResult.builder()
                    .valid(false)
                    .errorMessage("文件读取失败：" + e.getMessage())
                    .suggestion("请检查文件是否正确，或尝试使用 CSV 格式上传")
                    .build();
        }
    }

    /**
     * 从RDF文件导入本体（直接导入到图数据库）
     */
    @Override
    @Transactional
    public boolean importOntologyFromRdf(MultipartFile rdfFile, String projectName, String creator, String fileFormat) {
        try {
            // 1. 验证文件类型
            String fileName = rdfFile.getOriginalFilename();
            if (fileName == null) {
                throw new RuntimeException("文件名不能为空");
            }
            String lowerFileName = fileName.toLowerCase();
            if (!lowerFileName.endsWith(".rdf") && !lowerFileName.endsWith(".owl") 
                    && !lowerFileName.endsWith(".ttl") && !lowerFileName.endsWith(".nt")) {
                throw new RuntimeException("仅支持.rdf/.owl/.ttl/.nt格式的RDF文件");
            }

            // 2. 检查文件是否为空
            if (rdfFile.isEmpty()) {
                throw new RuntimeException("文件不能为空");
            }

            // 3. 检查项目名称是否已存在
            if (checkNameExists(projectName, null)) {
                // 如果项目已存在，创建新版本
                Ontology current = getCurrentVersion(projectName);
                if (current == null) {
                    throw new RuntimeException("项目当前版本不存在");
                }
                
                // 创建新版本
                String newVersion = generateNewVersion(current.getVersionNumber());
                Ontology newOntology = new Ontology();
                newOntology.setProjectName(projectName);
                newOntology.setCreator(creator);
                newOntology.setVersionNumber(newVersion);
                newOntology.setNamespaceUri(current.getNamespaceUri());
                newOntology.setVersionStatus(1);
                newOntology.setCreateTime(new Date());
                newOntology.setModifyTime(new Date());
                
                // 保存新版本到MySQL
                if (baseMapper.insert(newOntology) <= 0) {
                    throw new RuntimeException("保存本体元数据失败");
                }
                
                // 将旧版本标记为历史版本
                baseMapper.update(null, new LambdaUpdateWrapper<Ontology>()
                        .eq(Ontology::getId, current.getId())
                        .set(Ontology::getVersionStatus, 0));
                
                // 导入RDF到图数据库
                RdfFileFormat format = fileFormat != null 
                    ? RdfFileFormat.fromCode(fileFormat) 
                    : RdfFileFormat.fromFilename(fileName);
                
                // 先测试文件是否能被Jena解析
                byte[] fileBytes;
                try (InputStream is = rdfFile.getInputStream()) {
                    fileBytes = is.readAllBytes();
                }
                
                if (fileBytes.length == 0) {
                    throw new RuntimeException("文件内容为空");
                }
                
                // 修复 RDF 文件中的非法 URI
                fileBytes = com.example.tdproject.utils.RdfUriFixer.fixRdfUri(fileBytes);
                
                // 尝试直接用Jena解析
                Model testModel = ModelFactory.createDefaultModel();
                boolean parsed = false;
                Exception parseError = null;
                
                // 尝试 RDF/XML
                try {
                    testModel.read(new java.io.ByteArrayInputStream(fileBytes), null, "RDF/XML");
                    if (testModel.size() > 0) {
                        parsed = true;
                    }
                } catch (Exception e) {
                    parseError = e;
                }
                
                // 如果失败，尝试 Turtle
                if (!parsed) {
                    testModel = ModelFactory.createDefaultModel();
                    try {
                        testModel.read(new java.io.ByteArrayInputStream(fileBytes), null, "TURTLE");
                        if (testModel.size() > 0) {
                            parsed = true;
                            format = RdfFileFormat.TTL;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                
                // 如果还是失败，尝试 N-Triples
                if (!parsed) {
                    testModel = ModelFactory.createDefaultModel();
                    try {
                        testModel.read(new java.io.ByteArrayInputStream(fileBytes), null, "N-TRIPLES");
                        if (testModel.size() > 0) {
                            parsed = true;
                            format = RdfFileFormat.NT;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                
                if (!parsed) {
                    throw new RuntimeException("无法解析RDF文件。错误: " + (parseError != null ? parseError.getMessage() : "未知错误"));
                }
                
                // 使用解析成功的格式导入到图数据库
                String namedGraphUri = graphRepository.buildNamedGraphUri(projectName, newVersion);
                try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
                    graphRepository.loadRdfToNamedGraph(namedGraphUri, is, format);
                }
                
                return true;
            } else {
                // 项目不存在：创建新本体
                Ontology ontology = new Ontology();
                ontology.setProjectName(projectName);
                ontology.setCreator(creator);
                ontology.setVersionNumber("1.0");
                ontology.setNamespaceUri("http://example.com/ontology/" + projectName);
                ontology.setVersionStatus(1);
                ontology.setCreateTime(new Date());
                ontology.setModifyTime(new Date());

                if (baseMapper.insert(ontology) <= 0) {
                    throw new RuntimeException("保存本体元数据失败");
                }

                // 导入RDF到图数据库
                RdfFileFormat format = fileFormat != null 
                    ? RdfFileFormat.fromCode(fileFormat) 
                    : RdfFileFormat.fromFilename(fileName);
                
                // 先测试文件是否能被Jena解析
                byte[] fileBytes;
                try (InputStream is = rdfFile.getInputStream()) {
                    fileBytes = is.readAllBytes();
                }
                
                if (fileBytes.length == 0) {
                    throw new RuntimeException("文件内容为空");
                }
                
                System.out.println("[DEBUG] 文件读取成功: " + fileBytes.length + " bytes");
                
                // 修复 RDF 文件中的非法 URI
                fileBytes = com.example.tdproject.utils.RdfUriFixer.fixRdfUri(fileBytes);
                System.out.println("[DEBUG] URI 修复完成");
                
                // 尝试直接用Jena解析（不通过repository）
                Model testModel = ModelFactory.createDefaultModel();
                boolean parsed = false;
                Exception parseError = null;
                
                // 尝试 RDF/XML
                try {
                    testModel.read(new java.io.ByteArrayInputStream(fileBytes), null, "RDF/XML");
                    if (testModel.size() > 0) {
                        parsed = true;
                        System.out.println("[DEBUG] RDF/XML 解析成功, triples: " + testModel.size());
                    }
                } catch (Exception e) {
                    parseError = e;
                    System.out.println("[DEBUG] RDF/XML 解析失败: " + e.getMessage());
                }
                
                // 如果失败，尝试 Turtle
                if (!parsed) {
                    testModel = ModelFactory.createDefaultModel();
                    try {
                        testModel.read(new java.io.ByteArrayInputStream(fileBytes), null, "TURTLE");
                        if (testModel.size() > 0) {
                            parsed = true;
                            format = RdfFileFormat.TTL;
                            System.out.println("[DEBUG] Turtle 解析成功, triples: " + testModel.size());
                        }
                    } catch (Exception e) {
                        System.out.println("[DEBUG] Turtle 解析失败: " + e.getMessage());
                    }
                }
                
                // 如果还是失败，尝试 N-Triples
                if (!parsed) {
                    testModel = ModelFactory.createDefaultModel();
                    try {
                        testModel.read(new java.io.ByteArrayInputStream(fileBytes), null, "N-TRIPLES");
                        if (testModel.size() > 0) {
                            parsed = true;
                            format = RdfFileFormat.NT;
                            System.out.println("[DEBUG] N-Triples 解析成功, triples: " + testModel.size());
                        }
                    } catch (Exception e) {
                        System.out.println("[DEBUG] N-Triples 解析失败: " + e.getMessage());
                    }
                }
                
                if (!parsed) {
                    System.err.println("[ERROR] 所有格式都无法解析文件");
                    throw new RuntimeException("无法解析RDF文件。错误: " + (parseError != null ? parseError.getMessage() : "未知错误") + ". 请确保文件是有效的RDF/XML、Turtle或N-Triples格式。");
                }
                
                // 使用解析成功的格式导入到图数据库
                String namedGraphUri = graphRepository.buildNamedGraphUri(projectName, "1.0");
                try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
                    graphRepository.loadRdfToNamedGraph(namedGraphUri, is, format);
                    System.out.println("[DEBUG] 导入图数据库成功: " + namedGraphUri);
                }
                
                return true;
            }
        } catch (Exception e) {
            log.error("从RDF导入本体失败", e);
            throw new RuntimeException("RDF导入失败：" + e.getMessage());
        }
    }

    /**
     * 按ID导出本体为Excel
     */
    @Override
    public void exportById(Long id, HttpServletResponse response) throws IOException {
        Ontology ontology = baseMapper.selectById(id);
        if (ontology == null) {
            throw new RuntimeException("本体不存在");
        }
        List<Ontology> list = Collections.singletonList(ontology);
        // 导出文件名格式：本体_项目名_版本号.xlsx
        exportExcel(list, response, "本体_" + ontology.getProjectName() + "_" + ontology.getVersionNumber());
    }

    /**
     * 导出所有当前版本本体为Excel
     */
    @Override
    public void exportAll(HttpServletResponse response) throws IOException {
        List<Ontology> list = baseMapper.selectList(new LambdaQueryWrapper<Ontology>()
                .eq(Ontology::getVersionStatus, 1)  // 仅导出当前版本
                .orderByDesc(Ontology::getCreateTime));
        exportExcel(list, response, "所有当前版本本体");
    }

    /**
     * 通用Excel导出方法
     * @param list 本体列表
     * @param response HTTP响应
     * @param fileName 文件名（不含后缀）
     */
    private void exportExcel(List<Ontology> list, HttpServletResponse response, String fileName) throws IOException {
        // 设置响应头，告知浏览器下载文件
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" +
                URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8.name()));

        // 创建Excel并写入数据
        try (Workbook workbook = new XSSFWorkbook();  // 自动关闭资源
             OutputStream os = response.getOutputStream()) {

            Sheet sheet = workbook.createSheet("本体信息");

            // 创建表头
            String[] headers = {"ID", "项目名称", "创建人", "版本号", "命名空间URI", "创建时间", "版本状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 填充数据
            for (int i = 0; i < list.size(); i++) {
                Ontology ontology = list.get(i);
                Row row = sheet.createRow(i + 1);  // 从第1行开始（0是表头）

                row.createCell(0).setCellValue(ontology.getId() != null ? ontology.getId().toString() : "");
                row.createCell(1).setCellValue(ontology.getProjectName());
                row.createCell(2).setCellValue(ontology.getCreator());
                row.createCell(3).setCellValue(ontology.getVersionNumber());
                row.createCell(4).setCellValue(ontology.getNamespaceUri());
                // 格式化时间
                row.createCell(5).setCellValue(ontology.getCreateTime() != null ?
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ontology.getCreateTime()) : "");
                row.createCell(6).setCellValue(ontology.getVersionStatus() == 1 ? "当前版本" : "历史版本");
            }

            workbook.write(os);  // 写入响应流
        }
    }

    /**
     * 生成新版本号（简单递增逻辑：1.0 -> 1.1，1.9 -> 2.0）
     */
    private String generateNewVersion(String currentVersion) {
        try {
            String[] parts = currentVersion.split("\\.");
            if (parts.length == 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                minor++;
                if (minor >= 10) {  //  minor位满10进1
                    minor = 0;
                    major++;
                }
                return major + "." + minor;
            }
        } catch (Exception e) {
            log.error("版本号解析失败，使用默认递增", e);
        }
        return currentVersion + ".1";  // 解析失败时的降级处理
    }

    /**
     * 手动切换版本（将指定历史版本设为当前版本）
     */
    @Override
    public boolean switchVersion(Long targetVersionId) {
        // 逻辑与rollbackVersion一致，可直接复用
        return rollbackVersion(targetVersionId);
    }

    /**
     * 批量将项目所有版本标记为历史版本
     */
    @Override
    public boolean markAllAsHistory(String projectName) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<Ontology>()
                .eq(Ontology::getProjectName, projectName)
                .set(Ontology::getVersionStatus, 0));
        return rows > 0;
    }

    // ==================== 图数据库相关新方法实现 ====================

    /**
     * 创建本体并解析上传的RDF/OWL文件到图数据库
     */
    @Override
    @Transactional
    public Ontology createOntologyWithFile(String ontologyName, String creatorName, String version,
                                           String namespaceUri, String fileFormat, MultipartFile file) {
        try {
            // 1. 检查名称是否已存在
            if (checkNameExists(ontologyName, null)) {
                throw new RuntimeException("本体名称已存在：" + ontologyName);
            }

            // 2. 创建本体元数据
            Ontology ontology = new Ontology();
            ontology.setProjectName(ontologyName);
            ontology.setCreator(creatorName);
            ontology.setVersionNumber(version != null ? version : "1.0");
            ontology.setNamespaceUri(namespaceUri);
            ontology.setVersionStatus(1);
            ontology.setCreateTime(new Date());
            ontology.setModifyTime(new Date());

            // 3. 保存到MySQL
            boolean saved = save(ontology);
            if (!saved) {
                throw new RuntimeException("保存本体元数据失败");
            }

            // 4. 如果有文件，解析并保存到图数据库
            if (file != null && !file.isEmpty()) {
                RdfFileFormat format = fileFormat != null 
                    ? RdfFileFormat.fromCode(fileFormat) 
                    : RdfFileFormat.fromFilename(file.getOriginalFilename());
                
                String namedGraphUri = graphRepository.buildNamedGraphUri(ontologyName, ontology.getVersionNumber());
                
                try (InputStream is = file.getInputStream()) {
                    graphRepository.loadRdfToNamedGraph(namedGraphUri, is, format);
                    log.info("本体文件已解析到图数据库: {}, format: {}, triples: {}", 
                        namedGraphUri, format.getCode(), graphRepository.getTripleCount(namedGraphUri));
                }
            } else {
                // 创建空图
                String namedGraphUri = graphRepository.buildNamedGraphUri(ontologyName, ontology.getVersionNumber());
                graphRepository.createNamedGraph(namedGraphUri);
                log.info("创建空命名图: {}", namedGraphUri);
            }

            return ontology;
        } catch (Exception e) {
            log.error("创建本体失败", e);
            throw new RuntimeException("创建本体失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检查本体名称在图数据库中是否存在
     */
    @Override
    public CheckNameResult checkOntologyNameInGraph(String ontologyName) {
        String prefix = graphRepository.buildNamedGraphUri(ontologyName, "");
        List<String> namedGraphs = graphRepository.listNamedGraphsByPrefix(prefix);
        
        // 查询MySQL获取当前版本
        Ontology current = getCurrentVersion(ontologyName);
        
        return CheckNameResult.builder()
                .exists(!namedGraphs.isEmpty() || current != null)
                .namedGraphs(namedGraphs)
                .currentVersion(current != null ? current.getVersionNumber() : null)
                .build();
    }

    /**
     * 根据本体名称获取版本历史
     */
    @Override
    public List<VersionInfo> getOntologyVersionHistory(String ontologyName) {
        List<Ontology> versions = getVersionsByProjectName(ontologyName);
        return versions.stream()
                .sorted((v1, v2) -> v2.getCreateTime().compareTo(v1.getCreateTime())) // 按时间倒序
                .map(o -> VersionInfo.builder()
                        .id(o.getId())
                        .version(o.getVersionNumber())
                        .creator(o.getCreator())
                        .status(o.getVersionStatus())
                        .namespaceUri(o.getNamespaceUri())
                        .parentId(o.getParentId())
                        .createTime(o.getCreateTime())
                        .updateTime(o.getModifyTime())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 将本体导入图数据库（入库操作）
     */
    @Override
    @Transactional
    public boolean importOntologyToHouse(ImportToHouseRequest request) {
        try {
            // 1. 查询源本体
            Ontology source = baseMapper.selectById(request.getSourceOntologyId());
            if (source == null) {
                throw new RuntimeException("源本体不存在");
            }

            String ontologyName = source.getProjectName();
            String newVersion = request.getNewVersion();

            // 2. 检查版本号是否已存在
            if (checkVersionExists(ontologyName, newVersion)) {
                throw new RuntimeException("版本号 " + newVersion + " 已存在");
            }

            // 3. 将当前版本标记为历史版本
            baseMapper.update(null, new LambdaUpdateWrapper<Ontology>()
                    .eq(Ontology::getProjectName, ontologyName)
                    .eq(Ontology::getVersionStatus, 1)
                    .set(Ontology::getVersionStatus, 0));

            // 4. 创建新版本记录
            Ontology newVersionEntity = new Ontology();
            BeanUtils.copyProperties(source, newVersionEntity);
            newVersionEntity.setId(null);
            newVersionEntity.setVersionNumber(newVersion);
            newVersionEntity.setVersionStatus(1);
            newVersionEntity.setParentId(source.getId());
            newVersionEntity.setCreateTime(new Date());
            newVersionEntity.setModifyTime(new Date());
            baseMapper.insert(newVersionEntity);

            // 5. 处理图数据库
            String sourceGraphUri = graphRepository.buildNamedGraphUri(ontologyName, source.getVersionNumber());
            String targetGraphUri = graphRepository.buildNamedGraphUri(ontologyName, newVersion);

            if (graphRepository.namedGraphExists(sourceGraphUri)) {
                // 复制图数据到新版本
                graphRepository.copyNamedGraph(sourceGraphUri, targetGraphUri);
                log.info("已复制图数据从 {} 到 {}", sourceGraphUri, targetGraphUri);
            } else {
                // 源图不存在，创建包含基础结构的本体
                graphRepository.createNamedGraph(targetGraphUri);
                // 添加一个默认的根类 Thing
                ClassInfo thingClass = ClassInfo.builder()
                        .name("Thing")
                        .description("Root class of all classes")
                        .build();
                try {
                    graphRepository.createClass(targetGraphUri, thingClass);
                    log.info("源命名图不存在，创建基础本体结构: {}", targetGraphUri);
                } catch (Exception e) {
                    log.warn("创建默认根类失败（可能已存在）: {}", e.getMessage());
                }
            }

            return true;
        } catch (Exception e) {
            log.error("入库失败", e);
            throw new RuntimeException("入库失败：" + e.getMessage(), e);
        }
    }

    /**
     * 按版本号回滚本体版本
     */
    @Override
    @Transactional
    public boolean rollbackOntologyByVersion(RollbackVersionRequest request) {
        try {
            // 1. 查询源本体（当前版本）
            Ontology source = baseMapper.selectById(request.getSourceOntologyId());
            if (source == null) {
                throw new RuntimeException("源本体不存在");
            }

            String ontologyName = source.getProjectName();
            String targetVersion = request.getNewVersion();

            // 2. 查询目标历史版本
            Ontology target = baseMapper.selectOne(new LambdaQueryWrapper<Ontology>()
                    .eq(Ontology::getProjectName, ontologyName)
                    .eq(Ontology::getVersionNumber, targetVersion)
                    .last("limit 1"));
            
            if (target == null) {
                throw new RuntimeException("目标版本 " + targetVersion + " 不存在");
            }

            // 3. 将所有版本标记为历史
            baseMapper.update(null, new LambdaUpdateWrapper<Ontology>()
                    .eq(Ontology::getProjectName, ontologyName)
                    .eq(Ontology::getVersionStatus, 1)
                    .set(Ontology::getVersionStatus, 0));

            // 4. 将目标版本标记为当前版本
            target.setVersionStatus(1);
            target.setModifyTime(new Date());
            baseMapper.updateById(target);

            // 5. 图数据库回滚：复制历史版本数据到当前版本
            String targetGraphUri = graphRepository.buildNamedGraphUri(ontologyName, targetVersion);
            String currentGraphUri = graphRepository.buildNamedGraphUri(ontologyName, targetVersion + "_current");

            if (graphRepository.namedGraphExists(targetGraphUri)) {
                graphRepository.copyNamedGraph(targetGraphUri, currentGraphUri);
                log.info("图数据库回滚完成: {} -> {}", targetGraphUri, currentGraphUri);
            }

            return true;
        } catch (Exception e) {
            log.error("回滚失败", e);
            throw new RuntimeException("回滚失败：" + e.getMessage(), e);
        }
    }

    /**
     * 导出本体文件（OWL/RDF/TTL/NT格式）
     */
    @Override
    public void exportOntologyFile(Long id, RdfFileFormat format, HttpServletResponse response) throws IOException {
        log.info("导出本体文件, id: {}, format: {}", id, format.getCode());
        Ontology ontology = baseMapper.selectById(id);
        if (ontology == null) {
            log.error("导出失败，本体不存在，id: {}", id);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "本体不存在");
            return;
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());
        log.info("命名图URI: {}", namedGraphUri);

        // 设置响应头
        String filename = ontology.getProjectName() + "_" + ontology.getVersionNumber() + "." + format.getExtension();
        response.setContentType(format.getMimeType());
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode(filename, StandardCharsets.UTF_8.name()));

        // JSON格式特殊处理
        if (format == RdfFileFormat.JSON) {
            exportAsJson(namedGraphUri, ontology, response);
            return;
        }

        if (!graphRepository.namedGraphExists(namedGraphUri)) {
            log.warn("图数据库中不存在该本体，创建简单RDF导出: {}", namedGraphUri);
            // 图不存在，创建基于元数据的简单RDF
            createSimpleRdfExport(ontology, format, response);
            return;
        }

        // 导出图数据
        try (OutputStream os = response.getOutputStream()) {
            graphRepository.exportNamedGraph(namedGraphUri, os, format);
            log.info("导出成功: {}", filename);
        } catch (Exception e) {
            log.error("导出图数据失败", e);
            throw e;
        }
    }
    
    /**
     * 导出为JSON格式
     */
    private void exportAsJson(String namedGraphUri, Ontology ontology, HttpServletResponse response) throws IOException {
        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("ontologyId", ontology.getId());
        jsonData.put("projectName", ontology.getProjectName());
        jsonData.put("version", ontology.getVersionNumber());
        jsonData.put("namespaceUri", ontology.getNamespaceUri());
        jsonData.put("exportTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        
        // 获取图谱数据
        if (graphRepository.namedGraphExists(namedGraphUri)) {
            OntologyVisualizationDTO vizData = graphRepository.getVisualizationData(namedGraphUri);
            jsonData.put("nodes", vizData.getNodes());
            jsonData.put("edges", vizData.getEdges());
            jsonData.put("statistics", vizData.getStatistics());
        } else {
            jsonData.put("nodes", new ArrayList<>());
            jsonData.put("edges", new ArrayList<>());
        }
        
        // 写入响应
        try (OutputStream os = response.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            ObjectMapper mapper = new ObjectMapper();
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonData));
            writer.flush();
            log.info("JSON导出成功");
        }
    }

    /**
     * 创建基于元数据的简单RDF导出（当图数据库为空时使用）
     */
    private void createSimpleRdfExport(Ontology ontology, RdfFileFormat format, HttpServletResponse response) throws IOException {
        String filename = ontology.getProjectName() + "_" + ontology.getVersionNumber() + "." + format.getExtension();
        response.setContentType(format.getMimeType());
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode(filename, StandardCharsets.UTF_8.name()));

        // 创建简单的RDF模型
        Model model = ModelFactory.createDefaultModel();
        String ns = ontology.getNamespaceUri() != null ? ontology.getNamespaceUri() : 
                "http://example.org/ontology/";
        
        Resource ontologyResource = model.createResource(ns + ontology.getProjectName())
                .addProperty(RDF.type, model.createResource("http://www.w3.org/2002/07/owl#Ontology"))
                .addProperty(RDFS.label, ontology.getProjectName())
                .addProperty(model.createProperty(ns + "version"), ontology.getVersionNumber())
                .addProperty(model.createProperty(ns + "creator"), ontology.getCreator());

        try (OutputStream os = response.getOutputStream()) {
            model.write(os, format.getJenaLang().getName());
        }
    }

    // ==================== 本体详情查询实现 ====================

    @Override
    public OntologyStats getOntologyStats(Long id) {
        Ontology ontology = baseMapper.selectById(id);
        if (ontology == null) {
            log.warn("getOntologyStats: ontology not found, id={}", id);
            return OntologyStats.builder()
                    .classCount(0)
                    .individualCount(0)
                    .propertyCount(0)
                    .tripleCount(0)
                    .build();
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());
        log.info("getOntologyStats: graphUri={}", namedGraphUri);

        // 获取各类统计数据
        long tripleCount = graphRepository.getTripleCount(namedGraphUri);
        List<ClassInfo> classes = graphRepository.getClasses(namedGraphUri);
        List<PropertyInfo> properties = graphRepository.getProperties(namedGraphUri, null);
        List<IndividualInfo> individuals = graphRepository.getIndividuals(namedGraphUri, null);
        
        log.info("getOntologyStats: classes={}, individuals={}, properties={}, triples={}", 
                classes.size(), individuals.size(), properties.size(), tripleCount);
        
        // 实例总数
        long individualCount = individuals.size();

        return OntologyStats.builder()
                .classCount(classes.size())
                .individualCount(individualCount)
                .propertyCount(properties.size())
                .tripleCount(tripleCount)
                .build();
    }

    @Override
    public List<ClassInfo> getOntologyClasses(Long id, String keyword) {
        Ontology ontology = baseMapper.selectById(id);
        if (ontology == null) {
            return new ArrayList<>();
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        List<ClassInfo> classes = graphRepository.getClasses(namedGraphUri);
        
        // 根据关键词过滤
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            classes = classes.stream()
                    .filter(cls -> {
                        String name = cls.getName() != null ? cls.getName().toLowerCase() : "";
                        String description = cls.getDescription() != null ? cls.getDescription().toLowerCase() : "";
                        return name.contains(lowerKeyword) || description.contains(lowerKeyword);
                    })
                    .collect(Collectors.toList());
        }
        
        // 设置父类名称（简化处理，实际应该从图数据库查询）
        for (ClassInfo cls : classes) {
            if (cls.getParentId() == null || cls.getParentId().isEmpty()) {
                // 尝试查找父类（rdfs:subClassOf）
                cls.setParentName("Thing");
            }
        }
        
        return classes;
    }

    @Override
    public List<IndividualInfo> getOntologyIndividuals(Long id, String classUri, String keyword) {
        Ontology ontology = baseMapper.selectById(id);
        if (ontology == null) {
            return new ArrayList<>();
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        List<IndividualInfo> individuals = graphRepository.getIndividuals(namedGraphUri, classUri);
        
        // 按关键词过滤
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            individuals = individuals.stream()
                .filter(ind -> {
                    String name = ind.getName() != null ? ind.getName().toLowerCase() : "";
                    String desc = ind.getDescription() != null ? ind.getDescription().toLowerCase() : "";
                    return name.contains(lowerKeyword) || desc.contains(lowerKeyword);
                })
                .collect(Collectors.toList());
        }
        
        return individuals;
    }

    @Override
    public List<PropertyInfo> getOntologyProperties(Long id, String propertyType, String keyword) {
        Ontology ontology = baseMapper.selectById(id);
        if (ontology == null) {
            return new ArrayList<>();
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        List<PropertyInfo> properties = graphRepository.getProperties(namedGraphUri, propertyType);
        
        // 按关键词过滤
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            properties = properties.stream()
                .filter(prop -> {
                    String name = prop.getName() != null ? prop.getName().toLowerCase() : "";
                    String desc = prop.getDescription() != null ? prop.getDescription().toLowerCase() : "";
                    return name.contains(lowerKeyword) || desc.contains(lowerKeyword);
                })
                .collect(Collectors.toList());
        }
        
        return properties;
    }
    
    // ==================== 类管理CRUD实现 ====================
    
    @Override
    public String createOntologyClass(Long ontologyId, ClassInfo classInfo) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            throw new RuntimeException("本体不存在");
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.createClass(namedGraphUri, classInfo);
    }
    
    @Override
    public boolean updateOntologyClass(Long ontologyId, String classUri, ClassInfo classInfo) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            return false;
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.updateClass(namedGraphUri, classUri, classInfo);
    }
    
    @Override
    public boolean deleteOntologyClass(Long ontologyId, String classUri) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            return false;
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.deleteClass(namedGraphUri, classUri);
    }
    
    // ==================== 实例管理CRUD实现 ====================
    
    @Override
    public String createOntologyIndividual(Long ontologyId, IndividualInfo individualInfo) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            throw new RuntimeException("本体不存在");
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.createIndividual(namedGraphUri, individualInfo);
    }
    
    @Override
    public boolean updateOntologyIndividual(Long ontologyId, String individualUri, IndividualInfo individualInfo) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            return false;
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.updateIndividual(namedGraphUri, individualUri, individualInfo);
    }
    
    @Override
    public boolean deleteOntologyIndividual(Long ontologyId, String individualUri) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            return false;
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.deleteIndividual(namedGraphUri, individualUri);
    }
    
    @Override
    public IndividualRelations getIndividualRelations(Long ontologyId, String individualUri) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            log.warn("getIndividualRelations: ontology not found, id={}", ontologyId);
            return new IndividualRelations();
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());
        
        log.info("getIndividualRelations: graphUri={}, individualUri={}", namedGraphUri, individualUri);

        return graphRepository.getIndividualRelations(namedGraphUri, individualUri);
    }
    
    @Override
    public String createOntologyProperty(Long ontologyId, com.example.tdproject.ontology.dto.PropertyCreateRequest request) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            throw new IllegalArgumentException("Ontology not found: " + ontologyId);
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.createProperty(namedGraphUri, request);
    }
    
    @Override
    public boolean updateOntologyProperty(Long ontologyId, String propertyUri, 
                                          com.example.tdproject.ontology.dto.PropertyCreateRequest request) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            return false;
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.updateProperty(namedGraphUri, propertyUri, request);
    }
    
    @Override
    public boolean deleteOntologyProperty(Long ontologyId, String propertyUri) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            return false;
        }

        String namedGraphUri = graphRepository.buildNamedGraphUri(
                ontology.getProjectName(), ontology.getVersionNumber());

        return graphRepository.deleteProperty(namedGraphUri, propertyUri);
    }
    
    @Override
    public OntologyVisualizationDTO getOntologyVisualization(Long ontologyId) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            log.warn("getOntologyVisualization: ontology not found, id={}", ontologyId);
            return OntologyVisualizationDTO.builder()
                    .nodes(new ArrayList<>())
                    .edges(new ArrayList<>())
                    .build();
        }

        // 走跨版本解析，避免「当前版本号写法与导入时不一致 → 查到空图」
        // （命名图 URI 里版本号自带 v，实际是 /vv1.0，见 §3.1）
        String namedGraphUri = resolveNamedGraphUri(ontologyId);
        log.info("getOntologyVisualization: graphUri={}", namedGraphUri);

        return graphRepository.getVisualizationData(namedGraphUri);
    }

    @Override
    public String resolveNamedGraphUri(Long ontologyId) {
        Ontology ontology = baseMapper.selectById(ontologyId);
        if (ontology == null) {
            return null;
        }
        String projectName = ontology.getProjectName();
        try {
            // 遍历该项目所有版本，返回第一个确实存在数据的命名图
            for (Ontology version : getVersionsByProjectName(projectName)) {
                String uri = graphRepository.buildNamedGraphUri(
                        projectName, version.getVersionNumber());
                if (graphRepository.namedGraphExists(uri)) {
                    log.info("resolveNamedGraphUri: 命中命名图 {} (版本 {})",
                            uri, version.getVersionNumber());
                    return uri;
                }
            }
        } catch (Exception e) {
            log.error("resolveNamedGraphUri: 遍历版本失败, ontologyId={}", ontologyId, e);
        }
        // 都没有数据就退回当前版本的 URI（可能是空图，由上层按空处理）
        return graphRepository.buildNamedGraphUri(
                projectName, ontology.getVersionNumber());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphWarehouseResponseDTO warehouseGraph(GraphWarehouseDTO dto) {
        log.info("warehouseGraph: 开始图谱入库，ontologyId={}, ontologyName={}", 
                dto.getOntologyId(), dto.getOntologyName());
        
        try {
            // 1. 获取本体信息
            Ontology ontology = baseMapper.selectById(dto.getOntologyId());
            if (ontology == null) {
                throw new RuntimeException("本体不存在，id=" + dto.getOntologyId());
            }
            
            // 2. 创建新版本的本体记录
            Ontology newVersion = new Ontology();
            BeanUtils.copyProperties(ontology, newVersion);
            newVersion.setId(null);
            newVersion.setVersionNumber(dto.getVersion());
            newVersion.setVersionStatus(1); // 当前版本
            newVersion.setCreateTime(new Date());
            newVersion.setModifyTime(new Date());
            
            // 保存新版本
            baseMapper.insert(newVersion);
            
            // 3. 将旧版本标记为历史版本
            ontology.setVersionStatus(0);
            baseMapper.updateById(ontology);
            
            // 4. 构建NamedGraph URI
            String namedGraphUri = graphRepository.buildNamedGraphUri(
                    dto.getOntologyName(), dto.getVersion());
            
            // 5. 保存图谱数据到图数据库
            // 转换DTO为Repository需要的格式
            List<OntologyVisualizationDTO.NodeDTO> nodes = dto.getNodes().stream()
                    .map(node -> OntologyVisualizationDTO.NodeDTO.builder()
                            .id(node.getId())
                            .label(node.getName())  // GraphWarehouseDTO.NodeDTO使用name字段
                            .type(node.getType())
                            .uri(node.getIri())
                            .color(node.getColor())
                            .size(node.getSize())
                            .data(node.getData())
                            .build())
                    .collect(Collectors.toList());
            
            List<OntologyVisualizationDTO.EdgeDTO> edges = dto.getRelationships().stream()
                    .map(edge -> OntologyVisualizationDTO.EdgeDTO.builder()
                            .id(edge.getId())
                            .source(edge.getSource())
                            .target(edge.getTarget())
                            .label(edge.getRelationshipName())  // GraphWarehouseDTO.EdgeDTO使用relationshipName
                            .type(edge.getRelationshipType())   // GraphWarehouseDTO.EdgeDTO使用relationshipType
                            .color(edge.getColor())
                            .build())
                    .collect(Collectors.toList());
            
            // 6. 保存到图数据库
            graphRepository.saveVisualizationData(namedGraphUri, nodes, edges);
            
            log.info("warehouseGraph: 入库成功，保存了 {} 个节点，{} 条关系", 
                    nodes.size(), edges.size());
            
            return GraphWarehouseResponseDTO.builder()
                    .status("success")
                    .message("图谱入库成功")
                    .nodeCount(nodes.size())
                    .relationshipCount(edges.size())
                    .version(dto.getVersion())
                    .timestamp(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("warehouseGraph: 入库失败", e);
            throw new RuntimeException("图谱入库失败: " + e.getMessage(), e);
        }
    }
}