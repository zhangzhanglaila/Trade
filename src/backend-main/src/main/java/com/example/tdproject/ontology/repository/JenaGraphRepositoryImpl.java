package com.example.tdproject.ontology.repository;

import com.example.tdproject.ontology.dto.ClassInfo;
import com.example.tdproject.ontology.dto.IndividualInfo;
import com.example.tdproject.ontology.dto.OntologyVisualizationDTO;
import com.example.tdproject.ontology.dto.PropertyInfo;
import com.example.tdproject.ontology.enums.RdfFileFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Apache Jena TDB 图数据库实现
 */
@Repository
@Slf4j
public class JenaGraphRepositoryImpl implements GraphRepository {

    @Value("${ontology.graph.store-path:runtime/ontology-graph-store}")
    private String storePath;

    @Value("${ontology.graph.named-graph-prefix:http://example.org/ontology/}")
    private String namedGraphPrefix;

    private Dataset dataset;

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing Jena TDB at: {}", storePath);
            dataset = TDBFactory.createDataset(storePath);
            log.info("Jena TDB initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Jena TDB", e);
            throw new RuntimeException("Failed to initialize graph database", e);
        }
    }

    @PreDestroy
    public void close() {
        if (dataset != null) {
            try {
                dataset.close();
                log.info("Jena TDB closed successfully");
            } catch (Exception e) {
                log.error("Error closing Jena TDB", e);
            }
        }
    }

    @Override
    public boolean namedGraphExists(String namedGraphUri) {
        if (namedGraphUri == null || namedGraphUri.isEmpty()) {
            return false;
        }
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            boolean exists = dataset.containsNamedModel(namedGraphUri);
            dataset.commit();
            return exists;
        } catch (Exception e) {
            dataset.abort();
            return false;
        }
    }

    @Override
    public List<String> listNamedGraphs() {
        List<String> graphs = new ArrayList<>();
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            Iterator<String> names = dataset.listNames();
            while (names.hasNext()) {
                graphs.add(names.next());
            }
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
        }
        return graphs;
    }

    @Override
    public List<String> listNamedGraphsByPrefix(String prefix) {
        List<String> result = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) {
            return result;
        }
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            Iterator<String> names = dataset.listNames();
            while (names.hasNext()) {
                String name = names.next();
                if (name.startsWith(prefix)) {
                    result.add(name);
                }
            }
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
        }
        return result;
    }

    @Override
    public void createNamedGraph(String namedGraphUri) {
        if (namedGraphUri == null || namedGraphUri.isEmpty()) {
            throw new IllegalArgumentException("Named graph URI cannot be empty");
        }
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            // 如果已存在，先删除
            if (dataset.containsNamedModel(namedGraphUri)) {
                log.warn("Named graph {} already exists, will be replaced", namedGraphUri);
                dataset.removeNamedModel(namedGraphUri);
            }
            // 创建空模型
            dataset.addNamedModel(namedGraphUri, ModelFactory.createDefaultModel());
            dataset.commit();
            log.info("Created named graph: {}", namedGraphUri);
        } catch (Exception e) {
            dataset.abort();
            throw e;
        }
    }

    @Override
    public void deleteNamedGraph(String namedGraphUri) {
        if (namedGraphUri == null || namedGraphUri.isEmpty()) {
            return;
        }
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (dataset.containsNamedModel(namedGraphUri)) {
                dataset.removeNamedModel(namedGraphUri);
                dataset.commit();
                log.info("Deleted named graph: {}", namedGraphUri);
            } else {
                dataset.commit();
            }
        } catch (Exception e) {
            dataset.abort();
            throw e;
        }
    }

    @Override
    public void loadRdfToNamedGraph(String namedGraphUri, InputStream inputStream, RdfFileFormat format) {
        if (namedGraphUri == null || inputStream == null || format == null) {
            throw new IllegalArgumentException("Invalid parameters for loading RDF");
        }
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            // 创建新模型
            Model model = ModelFactory.createDefaultModel();
            // 读取输入流
            RDFDataMgr.read(model, inputStream, format.getJenaLang());
            // 保存到命名图
            if (dataset.containsNamedModel(namedGraphUri)) {
                dataset.removeNamedModel(namedGraphUri);
            }
            dataset.addNamedModel(namedGraphUri, model);
            dataset.commit();
            log.info("Loaded RDF to named graph: {}, triple count: {}", namedGraphUri, model.size());
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to load RDF to named graph: {}", namedGraphUri, e);
            throw new RuntimeException("Failed to load RDF data", e);
        }
    }

    @Override
    public void saveModelToNamedGraph(String namedGraphUri, Model model) {
        if (namedGraphUri == null || model == null) {
            throw new IllegalArgumentException("Invalid parameters for saving model");
        }
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (dataset.containsNamedModel(namedGraphUri)) {
                dataset.removeNamedModel(namedGraphUri);
            }
            dataset.addNamedModel(namedGraphUri, model);
            dataset.commit();
            log.info("Saved model to named graph: {}, triple count: {}", namedGraphUri, model.size());
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to save model to named graph: {}", namedGraphUri, e);
            throw new RuntimeException("Failed to save model", e);
        }
    }

    @Override
    public Model getModelFromNamedGraph(String namedGraphUri) {
        if (namedGraphUri == null) {
            return ModelFactory.createDefaultModel();
        }
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return ModelFactory.createDefaultModel();
            }
            // 返回模型副本，避免直接修改底层数据
            Model original = dataset.getNamedModel(namedGraphUri);
            Model copy = ModelFactory.createDefaultModel();
            copy.add(original);
            dataset.commit();
            return copy;
        } catch (Exception e) {
            dataset.abort();
            return ModelFactory.createDefaultModel();
        }
    }

    @Override
    public void exportNamedGraph(String namedGraphUri, OutputStream outputStream, RdfFileFormat format) {
        if (namedGraphUri == null || outputStream == null || format == null) {
            throw new IllegalArgumentException("Invalid parameters for export");
        }
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                throw new RuntimeException("Named graph not found: " + namedGraphUri);
            }
            Model model = dataset.getNamedModel(namedGraphUri);
            RDFDataMgr.write(outputStream, model, format.getJenaLang());
            dataset.commit();
            log.info("Exported named graph: {} in format: {}", namedGraphUri, format.getCode());
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to export named graph: {}", namedGraphUri, e);
            throw new RuntimeException("Failed to export RDF data", e);
        }
    }

    @Override
    public void copyNamedGraph(String sourceGraphUri, String targetGraphUri) {
        if (sourceGraphUri == null || targetGraphUri == null) {
            throw new IllegalArgumentException("Source and target URIs cannot be null");
        }
        // 使用 write 事务模式
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(sourceGraphUri)) {
                dataset.commit();
                throw new RuntimeException("Source named graph not found: " + sourceGraphUri);
            }
            Model sourceModel = dataset.getNamedModel(sourceGraphUri);
            Model copy = ModelFactory.createDefaultModel();
            copy.add(sourceModel);
            if (dataset.containsNamedModel(targetGraphUri)) {
                dataset.removeNamedModel(targetGraphUri);
            }
            dataset.addNamedModel(targetGraphUri, copy);
            dataset.commit();
            log.info("Copied named graph from {} to {}", sourceGraphUri, targetGraphUri);
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to copy named graph", e);
            throw new RuntimeException("Failed to copy named graph", e);
        }
    }

    @Override
    public long getTripleCount(String namedGraphUri) {
        if (namedGraphUri == null) {
            return 0;
        }
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return 0;
            }
            long count = dataset.getNamedModel(namedGraphUri).size();
            dataset.commit();
            return count;
        } catch (Exception e) {
            dataset.abort();
            return 0;
        }
    }

    @Override
    public String buildNamedGraphUri(String ontologyName, String version) {
        return namedGraphPrefix + ontologyName + "/v" + version;
    }

    @Override
    public String buildNamedGraphUri(String ontologyName, String version, long timestamp) {
        return namedGraphPrefix + ontologyName + "/v" + version + "/t" + timestamp;
    }

    // ==================== 本体详情查询实现 ====================

    @Override
    public List<ClassInfo> getClasses(String namedGraphUri) {
        List<ClassInfo> classes = new ArrayList<>();
        if (namedGraphUri == null) {
            log.warn("getClasses: namedGraphUri is null");
            return classes;
        }
        
        log.info("getClasses: querying graph: {}", namedGraphUri);
        
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            boolean exists = dataset.containsNamedModel(namedGraphUri);
            log.info("getClasses: graph exists = {}", exists);
            
            if (!exists) {
                // 列出所有可用的命名图
                List<String> allGraphs = listNamedGraphs();
                log.info("getClasses: available graphs: {}", allGraphs);
                dataset.commit();
                return classes;
            }
            
            // 检查三元组数量
            long tripleCount = dataset.getNamedModel(namedGraphUri).size();
            log.info("getClasses: triple count in graph = {}", tripleCount);
            
            // SPARQL查询：获取所有类（包括owl:Class和rdfs:Class）
            String queryString = 
                "SELECT DISTINCT ?class ?label ?comment WHERE { " +
                "  GRAPH <" + namedGraphUri + "> { " +
                "    { ?class a <" + OWL.Class.getURI() + "> } " +
                "    UNION { ?class a <" + RDFS.Class.getURI() + "> } " +
                "    UNION { ?class a <" + RDFS.Resource.getURI() + "> } " +
                "    OPTIONAL { ?class <" + RDFS.label.getURI() + "> ?label } " +
                "    OPTIONAL { ?class <" + RDFS.comment.getURI() + "> ?comment } " +
                "  } " +
                "}";
            
            log.info("getClasses: executing query: {}", queryString);
            
            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
                ResultSet results = qexec.execSelect();
                log.info("getClasses: query executed, processing results...");
                int count = 0;
                while (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    Resource classRes = soln.getResource("class");
                    String classUri = classRes.getURI();
                    String localName = classRes.getLocalName();
                    if (localName == null || localName.isEmpty()) {
                        localName = getLocalNameFromUri(classUri);
                    }
                    
                    String label = soln.contains("label") ? soln.getLiteral("label").getString() : localName;
                    String comment = soln.contains("comment") ? soln.getLiteral("comment").getString() : "";
                    
                    ClassInfo classInfo = ClassInfo.builder()
                            .id(classUri)
                            .name(label)
                            .uri(classUri)
                            .description(comment)
                            .individualCount(0) // 暂时设为0，避免嵌套事务
                            .build();
                    classes.add(classInfo);
                    count++;
                }
                log.info("getClasses: found {} classes", count);
            }
            
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            log.error("获取类列表失败", e);
        }
        
        return classes;
    }

    @Override
    public List<IndividualInfo> getIndividuals(String namedGraphUri, String classUri) {
        List<IndividualInfo> individuals = new ArrayList<>();
        if (namedGraphUri == null) {
            return individuals;
        }
        
        log.info("getIndividuals: querying graph: {}, classFilter: {}", namedGraphUri, classUri);
        
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                log.info("getIndividuals: graph not found");
                dataset.commit();
                return individuals;
            }
            
            // 检查三元组数量
            long tripleCount = dataset.getNamedModel(namedGraphUri).size();
            log.info("getIndividuals: triple count = {}", tripleCount);
            
            // 构建SPARQL查询 - 查找所有实例（包括NamedIndividual和普通实例）
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT DISTINCT ?individual ?label ?comment ?type WHERE { ");
            queryBuilder.append("  GRAPH <").append(namedGraphUri).append("> { ");
            // 查找所有有 rdf:type 的资源，但排除类、属性等元数据
            queryBuilder.append("    ?individual a ?type . ");
            queryBuilder.append("    FILTER (?type != <").append(OWL.Class.getURI()).append("> ");
            queryBuilder.append("        && ?type != <").append(RDFS.Class.getURI()).append("> ");
            queryBuilder.append("        && ?type != <").append(OWL.ObjectProperty.getURI()).append("> ");
            queryBuilder.append("        && ?type != <").append(OWL.DatatypeProperty.getURI()).append("> ");
            queryBuilder.append("        && ?type != <").append(OWL.AnnotationProperty.getURI()).append("> ");
            queryBuilder.append("        && ?type != <").append(RDFS.Resource.getURI()).append(">) ");
            if (classUri != null && !classUri.isEmpty()) {
                // 如果指定了类，还要求是该类的实例
                queryBuilder.append("    ?individual a <").append(classUri).append("> . ");
            }
            queryBuilder.append("    OPTIONAL { ?individual <").append(RDFS.label.getURI()).append("> ?label } ");
            queryBuilder.append("    OPTIONAL { ?individual <").append(RDFS.comment.getURI()).append("> ?comment } ");
            queryBuilder.append("  } ");
            // 硬性限流：本图约有 196 万实例，不加 LIMIT 会让接口长时间不返回
            queryBuilder.append("} LIMIT ").append(MAX_INDIVIDUALS);

            String queryString = queryBuilder.toString();
            log.debug("getIndividuals: SPARQL query: {}", queryString);
            
            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
                ResultSet results = qexec.execSelect();
                int count = 0;
                while (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    Resource indRes = soln.getResource("individual");
                    String indUri = indRes.getURI();
                    String localName = indRes.getLocalName();
                    if (localName == null || localName.isEmpty()) {
                        localName = getLocalNameFromUri(indUri);
                    }
                    
                    String label = soln.contains("label") ? soln.getLiteral("label").getString() : localName;
                    String comment = soln.contains("comment") ? soln.getLiteral("comment").getString() : "";
                    String typeUri = soln.contains("type") ? soln.getResource("type").getURI() : "";
                    String typeName = typeUri.isEmpty() ? "" : getLocalNameFromUri(typeUri);
                    
                    // 逐条日志必须降到 DEBUG：196 万条 INFO 会把日志文件写爆（实测数百 MB）
                    log.debug("getIndividuals: found individual: {} (type: {})", indUri, typeName);
                    
                    IndividualInfo indInfo = IndividualInfo.builder()
                            .id(indUri)
                            .name(label)
                            .uri(indUri)
                            .classId(typeUri)
                            .className(typeName)
                            .description(comment)
                            .build();
                    individuals.add(indInfo);
                    count++;
                }
                log.info("getIndividuals: total found: {}", count);
            }
            
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            log.error("获取实例列表失败", e);
        }
        
        return individuals;
    }

    @Override
    public List<PropertyInfo> getProperties(String namedGraphUri, String propertyType) {
        List<PropertyInfo> properties = new ArrayList<>();
        if (namedGraphUri == null) {
            return properties;
        }
        
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return properties;
            }
            
            // 根据类型选择查询
            if (propertyType == null || propertyType.isEmpty() || "object".equals(propertyType)) {
                // 查询对象属性 (owl:ObjectProperty)
                properties.addAll(queryPropertiesByType(namedGraphUri, OWL.ObjectProperty.getURI(), "object"));
            }
            if (propertyType == null || propertyType.isEmpty() || "datatype".equals(propertyType)) {
                // 查询数据属性 (owl:DatatypeProperty)
                properties.addAll(queryPropertiesByType(namedGraphUri, OWL.DatatypeProperty.getURI(), "datatype"));
            }
            if (propertyType == null || propertyType.isEmpty() || "annotation".equals(propertyType)) {
                // 查询注释属性 (owl:AnnotationProperty)
                properties.addAll(queryPropertiesByType(namedGraphUri, OWL.AnnotationProperty.getURI(), "annotation"));
            }
            
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            log.error("获取属性列表失败", e);
        }
        
        return properties;
    }
    
    private List<PropertyInfo> queryPropertiesByType(String namedGraphUri, String propertyTypeUri, String typeName) {
        List<PropertyInfo> properties = new ArrayList<>();
        
        log.info("queryPropertiesByType: graph={}, type={}, typeUri={}", namedGraphUri, typeName, propertyTypeUri);
        
        String queryString = 
            "SELECT ?property ?label ?comment ?domain ?range WHERE { " +
            "  GRAPH <" + namedGraphUri + "> { " +
            "    ?property a <" + propertyTypeUri + "> . " +
            "    OPTIONAL { ?property <" + RDFS.label.getURI() + "> ?label } " +
            "    OPTIONAL { ?property <" + RDFS.comment.getURI() + "> ?comment } " +
            "    OPTIONAL { ?property <" + RDFS.domain.getURI() + "> ?domain } " +
            "    OPTIONAL { ?property <" + RDFS.range.getURI() + "> ?range } " +
            "  } " +
            "}";
        
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet results = qexec.execSelect();
            int count = 0;
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                Resource propRes = soln.getResource("property");
                String propUri = propRes.getURI();
                String localName = propRes.getLocalName();
                if (localName == null || localName.isEmpty()) {
                    localName = getLocalNameFromUri(propUri);
                }
                
                String label = soln.contains("label") ? soln.getLiteral("label").getString() : localName;
                String comment = soln.contains("comment") ? soln.getLiteral("comment").getString() : "";
                String domainUri = soln.contains("domain") ? soln.getResource("domain").getURI() : "";
                String rangeUri = soln.contains("range") ? soln.getResource("range").getURI() : "";
                
                count++;
                log.debug("Found property: {} (type: {})", propUri, typeName);
                
                PropertyInfo propInfo = PropertyInfo.builder()
                        .id(propUri)
                        .name(label)
                        .uri(propUri)
                        .type(typeName)
                        .domain(getLocalNameFromUri(domainUri))
                        .range(getLocalNameFromUri(rangeUri))
                        .description(comment)
                        .build();
                properties.add(propInfo);
            }
            
            log.info("queryPropertiesByType: found {} properties of type {}", count, typeName);
        }
        
        return properties;
    }

    @Override
    public long getIndividualCountByClass(String namedGraphUri, String classUri) {
        if (namedGraphUri == null) {
            return 0;
        }
        
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return 0;
            }
            
            // classUri 为空时统计「全图有 rdf:type 的资源数」。
            // 本体统计接口只需要一个总数，用 COUNT 取即可；若为拿这个数字去调
            // getIndividuals(graph, null)，会遍历全部约 196 万实例，接口数分钟不返回。
            String queryString;
            if (classUri == null || classUri.isEmpty()) {
                queryString =
                    "SELECT (COUNT(DISTINCT ?individual) AS ?count) WHERE { " +
                    "  GRAPH <" + namedGraphUri + "> { ?individual a ?anyType } " +
                    "}";
            } else {
                queryString =
                    "SELECT (COUNT(?individual) AS ?count) WHERE { " +
                    "  GRAPH <" + namedGraphUri + "> { " +
                    "    ?individual a <" + classUri + "> . " +
                    "  } " +
                    "}";
            }
            
            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
                ResultSet results = qexec.execSelect();
                if (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    long count = soln.getLiteral("count").getLong();
                    dataset.commit();
                    return count;
                }
            }
            
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            log.error("获取实例数量失败", e);
        }
        
        return 0;
    }
    
    /**
     * 从URI中提取本地名称
     */
    private String getLocalNameFromUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "";
        }
        int lastSlash = uri.lastIndexOf('/');
        int lastHash = uri.lastIndexOf('#');
        int lastColon = uri.lastIndexOf(':');
        int start = Math.max(lastSlash, Math.max(lastHash, lastColon)) + 1;
        return uri.substring(start);
    }
    
    // ==================== 类/实例/属性的CRUD实现 ====================
    
    @Override
    public String createClass(String namedGraphUri, ClassInfo classInfo) {
        if (namedGraphUri == null || classInfo == null || classInfo.getName() == null) {
            throw new IllegalArgumentException("Invalid parameters for creating class");
        }
        
        log.info("createClass: graph={}, className={}", namedGraphUri, classInfo.getName());
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            boolean exists = dataset.containsNamedModel(namedGraphUri);
            log.info("createClass: graph exists = {}", exists);
            
            if (!exists) {
                // 如果图不存在，创建空图
                log.info("createClass: creating empty graph: {}", namedGraphUri);
                dataset.addNamedModel(namedGraphUri, ModelFactory.createDefaultModel());
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            
            // 构建类URI
            String namespace = namedGraphUri.substring(0, namedGraphUri.lastIndexOf('/')) + "/";
            String classUri = namespace + classInfo.getName();
            
            // 检查是否已存在
            Resource classRes = model.createResource(classUri);
            if (model.containsResource(classRes)) {
                dataset.commit();
                throw new RuntimeException("Class already exists: " + classInfo.getName());
            }
            
            // 创建类
            classRes.addProperty(RDF.type, OWL.Class);
            classRes.addProperty(RDFS.label, classInfo.getName());
            
            if (classInfo.getDescription() != null && !classInfo.getDescription().isEmpty()) {
                classRes.addProperty(RDFS.comment, classInfo.getDescription());
            }
            
            // 设置父类（rdfs:subClassOf）
            if (classInfo.getParentId() != null && !classInfo.getParentId().isEmpty()) {
                Resource parentRes = model.createResource(classInfo.getParentId());
                classRes.addProperty(RDFS.subClassOf, parentRes);
            } else {
                // 默认继承自 owl:Thing
                classRes.addProperty(RDFS.subClassOf, OWL.Thing);
            }
            
            dataset.commit();
            log.info("Created class: {} in graph: {}", classUri, namedGraphUri);
            return classUri;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to create class", e);
            throw new RuntimeException("Failed to create class: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean updateClass(String namedGraphUri, String classUri, ClassInfo classInfo) {
        if (namedGraphUri == null || classUri == null || classInfo == null) {
            return false;
        }
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return false;
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            Resource classRes = model.createResource(classUri);
            
            if (!model.containsResource(classRes)) {
                dataset.commit();
                return false;
            }
            
            // 移除旧的label和comment
            model.removeAll(classRes, RDFS.label, null);
            model.removeAll(classRes, RDFS.comment, null);
            model.removeAll(classRes, RDFS.subClassOf, null);
            
            // 添加新的属性
            if (classInfo.getName() != null && !classInfo.getName().isEmpty()) {
                classRes.addProperty(RDFS.label, classInfo.getName());
            }
            
            if (classInfo.getDescription() != null && !classInfo.getDescription().isEmpty()) {
                classRes.addProperty(RDFS.comment, classInfo.getDescription());
            }
            
            // 更新父类
            if (classInfo.getParentId() != null && !classInfo.getParentId().isEmpty()) {
                Resource parentRes = model.createResource(classInfo.getParentId());
                classRes.addProperty(RDFS.subClassOf, parentRes);
            } else {
                classRes.addProperty(RDFS.subClassOf, OWL.Thing);
            }
            
            dataset.commit();
            log.info("Updated class: {} in graph: {}", classUri, namedGraphUri);
            return true;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to update class", e);
            return false;
        }
    }
    
    @Override
    public boolean deleteClass(String namedGraphUri, String classUri) {
        if (namedGraphUri == null || classUri == null) {
            return false;
        }
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return false;
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            Resource classRes = model.createResource(classUri);
            
            if (!model.containsResource(classRes)) {
                dataset.commit();
                return false;
            }
            
            // 删除该类的所有陈述
            model.removeAll(classRes, null, null);
            model.removeAll(null, null, classRes);
            
            dataset.commit();
            log.info("Deleted class: {} from graph: {}", classUri, namedGraphUri);
            return true;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to delete class", e);
            return false;
        }
    }
    
    // ==================== 实例CRUD实现 ====================
    
    @Override
    public String createIndividual(String namedGraphUri, IndividualInfo individualInfo) {
        if (namedGraphUri == null || individualInfo == null || individualInfo.getName() == null) {
            throw new IllegalArgumentException("Invalid parameters for creating individual");
        }
        
        log.info("createIndividual: graph={}, name={}", namedGraphUri, individualInfo.getName());
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                log.info("createIndividual: creating empty graph: {}", namedGraphUri);
                dataset.addNamedModel(namedGraphUri, ModelFactory.createDefaultModel());
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            
            // 构建实例URI
            String namespace = namedGraphUri.substring(0, namedGraphUri.lastIndexOf('/')) + "/";
            String individualUri = namespace + individualInfo.getName();
            
            Resource indRes = model.createResource(individualUri);
            
            // 声明为NamedIndividual
            indRes.addProperty(RDF.type, OWL2.NamedIndividual);
            
            // 设置所属类
            if (individualInfo.getClassId() != null && !individualInfo.getClassId().isEmpty()) {
                Resource classRes = model.createResource(individualInfo.getClassId());
                indRes.addProperty(RDF.type, classRes);
            }
            
            // 设置label
            indRes.addProperty(RDFS.label, individualInfo.getName());
            
            // 设置描述
            if (individualInfo.getDescription() != null && !individualInfo.getDescription().isEmpty()) {
                indRes.addProperty(RDFS.comment, individualInfo.getDescription());
            }
            
            // 设置属性值
            if (individualInfo.getProperties() != null) {
                for (Map.Entry<String, Object> entry : individualInfo.getProperties().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue().toString();
                    
                    // 判断key是否已经是完整URI
                    String propUri;
                    if (key.startsWith("http://") || key.startsWith("https://")) {
                        propUri = key;
                    } else {
                        propUri = namespace + key;
                    }
                    
                    Property prop = model.createProperty(propUri);
                    
                    // 判断value是否也是URI（对象属性）
                    if (value.startsWith("http://") || value.startsWith("https://")) {
                        Resource targetRes = model.createResource(value);
                        indRes.addProperty(prop, targetRes);
                    } else {
                        // 数据属性
                        indRes.addProperty(prop, value);
                    }
                }
            }
            
            dataset.commit();
            log.info("Created individual: {} in graph: {}", individualUri, namedGraphUri);
            return individualUri;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to create individual", e);
            throw new RuntimeException("Failed to create individual: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean updateIndividual(String namedGraphUri, String individualUri, IndividualInfo individualInfo) {
        if (namedGraphUri == null || individualUri == null || individualInfo == null) {
            return false;
        }
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return false;
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            Resource indRes = model.createResource(individualUri);
            
            if (!model.containsResource(indRes)) {
                dataset.commit();
                return false;
            }
            
            // 移除旧的label和comment
            model.removeAll(indRes, RDFS.label, null);
            model.removeAll(indRes, RDFS.comment, null);
            
            // 更新label
            if (individualInfo.getName() != null && !individualInfo.getName().isEmpty()) {
                indRes.addProperty(RDFS.label, individualInfo.getName());
            }
            
            // 更新描述
            if (individualInfo.getDescription() != null) {
                if (!individualInfo.getDescription().isEmpty()) {
                    indRes.addProperty(RDFS.comment, individualInfo.getDescription());
                }
            }
            
            // 更新所属类
            if (individualInfo.getClassId() != null && !individualInfo.getClassId().isEmpty()) {
                // 先移除旧的类型（保留NamedIndividual）
                StmtIterator it = model.listStatements(indRes, RDF.type, (RDFNode) null);
                List<Resource> typesToRemove = new ArrayList<>();
                while (it.hasNext()) {
                    Statement stmt = it.next();
                    Resource type = stmt.getResource();
                    if (!type.getURI().equals(OWL2.NamedIndividual.getURI())) {
                        typesToRemove.add(type);
                    }
                }
                for (Resource type : typesToRemove) {
                    model.remove(indRes, RDF.type, type);
                }
                // 添加新类型
                Resource classRes = model.createResource(individualInfo.getClassId());
                indRes.addProperty(RDF.type, classRes);
            }
            
            // 更新属性值
            if (individualInfo.getProperties() != null) {
                String namespace = namedGraphUri.substring(0, namedGraphUri.lastIndexOf('/')) + "/";
                
                for (Map.Entry<String, Object> entry : individualInfo.getProperties().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue().toString();
                    
                    // 判断key是否已经是完整URI
                    String propUri;
                    if (key.startsWith("http://") || key.startsWith("https://")) {
                        propUri = key;
                    } else {
                        propUri = namespace + key;
                    }
                    
                    Property prop = model.createProperty(propUri);
                    
                    // 先移除该属性的旧值
                    model.removeAll(indRes, prop, null);
                    
                    // 添加新值
                    if (value.startsWith("http://") || value.startsWith("https://")) {
                        Resource targetRes = model.createResource(value);
                        indRes.addProperty(prop, targetRes);
                    } else {
                        indRes.addProperty(prop, value);
                    }
                }
            }
            
            dataset.commit();
            log.info("Updated individual: {} in graph: {}", individualUri, namedGraphUri);
            return true;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to update individual", e);
            return false;
        }
    }
    
    @Override
    public boolean deleteIndividual(String namedGraphUri, String individualUri) {
        if (namedGraphUri == null || individualUri == null) {
            return false;
        }
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return false;
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            Resource indRes = model.createResource(individualUri);
            
            if (!model.containsResource(indRes)) {
                dataset.commit();
                return false;
            }
            
            // 删除该实例的所有陈述
            model.removeAll(indRes, null, null);
            model.removeAll(null, null, indRes);
            
            dataset.commit();
            log.info("Deleted individual: {} from graph: {}", individualUri, namedGraphUri);
            return true;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to delete individual", e);
            return false;
        }
    }
    
    @Override
    public com.example.tdproject.ontology.dto.IndividualRelations getIndividualRelations(
            String namedGraphUri, String individualUri) {
        com.example.tdproject.ontology.dto.IndividualRelations relations = 
            new com.example.tdproject.ontology.dto.IndividualRelations();
        
        if (namedGraphUri == null || individualUri == null) {
            return relations;
        }
        
        log.info("getIndividualRelations: graph={}, individual={}", namedGraphUri, individualUri);
        
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return relations;
            }
            
            List<com.example.tdproject.ontology.dto.IndividualRelations.ObjectPropertyRelation> objectProps = 
                new ArrayList<>();
            List<com.example.tdproject.ontology.dto.IndividualRelations.DatatypePropertyRelation> datatypeProps = 
                new ArrayList<>();
            List<com.example.tdproject.ontology.dto.IndividualRelations.InverseRelation> inverseRelations = 
                new ArrayList<>();
            
            // 1. 查询对象属性关系（该实例指向其他实例）
            String objectPropQuery = 
                "SELECT ?prop ?propLabel ?target ?targetLabel WHERE { " +
                "  GRAPH <" + namedGraphUri + "> { " +
                "    <" + individualUri + "> ?prop ?target . " +
                "    FILTER (isIRI(?target)) " +
                "    FILTER (?prop != <" + RDF.type.getURI() + ">) " +
                "    OPTIONAL { ?prop <" + RDFS.label.getURI() + "> ?propLabel } " +
                "    OPTIONAL { ?target <" + RDFS.label.getURI() + "> ?targetLabel } " +
                "  } " +
                "}";
            
            Query query1 = QueryFactory.create(objectPropQuery);
            try (QueryExecution qexec1 = QueryExecutionFactory.create(query1, dataset)) {
                ResultSet results1 = qexec1.execSelect();
                while (results1.hasNext()) {
                    QuerySolution soln = results1.nextSolution();
                    com.example.tdproject.ontology.dto.IndividualRelations.ObjectPropertyRelation rel = 
                        new com.example.tdproject.ontology.dto.IndividualRelations.ObjectPropertyRelation();
                    
                    Resource propRes = soln.getResource("prop");
                    rel.setPropertyUri(propRes.getURI());
                    rel.setPropertyName(soln.contains("propLabel") ? 
                        soln.getLiteral("propLabel").getString() : getLocalNameFromUri(propRes.getURI()));
                    
                    Resource targetRes = soln.getResource("target");
                    rel.setTargetUri(targetRes.getURI());
                    rel.setTargetName(soln.contains("targetLabel") ? 
                        soln.getLiteral("targetLabel").getString() : getLocalNameFromUri(targetRes.getURI()));
                    
                    objectProps.add(rel);
                }
            }
            
            // 2. 查询数据属性（该实例的基本属性值）
            String datatypeQuery = 
                "SELECT ?prop ?propLabel ?value WHERE { " +
                "  GRAPH <" + namedGraphUri + "> { " +
                "    <" + individualUri + "> ?prop ?value . " +
                "    FILTER (isLiteral(?value)) " +
                "    OPTIONAL { ?prop <" + RDFS.label.getURI() + "> ?propLabel } " +
                "  } " +
                "}";
            
            Query query2 = QueryFactory.create(datatypeQuery);
            try (QueryExecution qexec2 = QueryExecutionFactory.create(query2, dataset)) {
                ResultSet results2 = qexec2.execSelect();
                while (results2.hasNext()) {
                    QuerySolution soln = results2.nextSolution();
                    com.example.tdproject.ontology.dto.IndividualRelations.DatatypePropertyRelation rel = 
                        new com.example.tdproject.ontology.dto.IndividualRelations.DatatypePropertyRelation();
                    
                    Resource propRes = soln.getResource("prop");
                    rel.setPropertyUri(propRes.getURI());
                    rel.setPropertyName(soln.contains("propLabel") ? 
                        soln.getLiteral("propLabel").getString() : getLocalNameFromUri(propRes.getURI()));
                    
                    Literal valueLit = soln.getLiteral("value");
                    rel.setValue(valueLit.getString());
                    
                    // 简化数据类型URI显示
                    String datatypeUri = valueLit.getDatatypeURI();
                    String datatypeLabel;
                    if (datatypeUri == null) {
                        datatypeLabel = "xsd:string";
                    } else if (datatypeUri.contains("#")) {
                        // 提取命名空间前缀，如 xsd:string
                        String ns = datatypeUri.substring(0, datatypeUri.indexOf('#'));
                        String local = datatypeUri.substring(datatypeUri.indexOf('#') + 1);
                        if (ns.contains("XMLSchema")) {
                            datatypeLabel = "xsd:" + local;
                        } else {
                            datatypeLabel = local;
                        }
                    } else if (datatypeUri.contains("/")) {
                        // 提取最后部分
                        datatypeLabel = datatypeUri.substring(datatypeUri.lastIndexOf('/') + 1);
                    } else {
                        datatypeLabel = datatypeUri;
                    }
                    rel.setDatatype(datatypeLabel);
                    
                    datatypeProps.add(rel);
                }
            }
            
            // 3. 查询反向关系（其他实例指向该实例）
            String inverseQuery = 
                "SELECT ?source ?sourceLabel ?prop ?propLabel WHERE { " +
                "  GRAPH <" + namedGraphUri + "> { " +
                "    ?source ?prop <" + individualUri + "> . " +
                "    FILTER (isIRI(?source)) " +
                "    OPTIONAL { ?prop <" + RDFS.label.getURI() + "> ?propLabel } " +
                "    OPTIONAL { ?source <" + RDFS.label.getURI() + "> ?sourceLabel } " +
                "  } " +
                "}";
            
            Query query3 = QueryFactory.create(inverseQuery);
            try (QueryExecution qexec3 = QueryExecutionFactory.create(query3, dataset)) {
                ResultSet results3 = qexec3.execSelect();
                while (results3.hasNext()) {
                    QuerySolution soln = results3.nextSolution();
                    com.example.tdproject.ontology.dto.IndividualRelations.InverseRelation rel = 
                        new com.example.tdproject.ontology.dto.IndividualRelations.InverseRelation();
                    
                    Resource sourceRes = soln.getResource("source");
                    rel.setSourceUri(sourceRes.getURI());
                    rel.setSourceName(soln.contains("sourceLabel") ? 
                        soln.getLiteral("sourceLabel").getString() : getLocalNameFromUri(sourceRes.getURI()));
                    
                    Resource propRes = soln.getResource("prop");
                    rel.setPropertyUri(propRes.getURI());
                    rel.setPropertyName(soln.contains("propLabel") ? 
                        soln.getLiteral("propLabel").getString() : getLocalNameFromUri(propRes.getURI()));
                    
                    inverseRelations.add(rel);
                }
            }
            
            dataset.commit();
            
            relations.setObjectProperties(objectProps);
            relations.setDatatypeProperties(datatypeProps);
            relations.setInverseRelations(inverseRelations);
            
            log.info("getIndividualRelations: found {} object props, {} datatype props, {} inverse relations",
                objectProps.size(), datatypeProps.size(), inverseRelations.size());
            
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to get individual relations", e);
        }
        
        return relations;
    }
    
    // ==================== 属性CRUD实现 ====================
    
    @Override
    public String createProperty(String namedGraphUri, com.example.tdproject.ontology.dto.PropertyCreateRequest request) {
        if (namedGraphUri == null || request == null || request.getName() == null) {
            throw new IllegalArgumentException("Invalid parameters for creating property");
        }
        
        log.info("createProperty: graph={}, name={}, type={}", namedGraphUri, request.getName(), request.getType());
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                log.info("createProperty: creating empty graph: {}", namedGraphUri);
                dataset.addNamedModel(namedGraphUri, ModelFactory.createDefaultModel());
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            
            // 构建属性URI
            String namespace = namedGraphUri.substring(0, namedGraphUri.lastIndexOf('/')) + "/";
            String propertyUri = namespace + request.getName();
            
            Property prop = model.createProperty(propertyUri);
            
            // 设置属性类型
            if ("object".equals(request.getType())) {
                prop.addProperty(RDF.type, OWL.ObjectProperty);
            } else if ("datatype".equals(request.getType())) {
                prop.addProperty(RDF.type, OWL.DatatypeProperty);
            } else {
                prop.addProperty(RDF.type, OWL.AnnotationProperty);
            }
            
            // 设置label
            prop.addProperty(RDFS.label, request.getName());
            
            // 设置定义域
            if (request.getDomain() != null && !request.getDomain().isEmpty()) {
                Resource domainRes = model.createResource(request.getDomain());
                prop.addProperty(RDFS.domain, domainRes);
            }
            
            // 设置值域
            if (request.getRange() != null && !request.getRange().isEmpty()) {
                Resource rangeRes;
                if (request.getRange().startsWith("http://") || request.getRange().startsWith("https://")) {
                    rangeRes = model.createResource(request.getRange());
                } else {
                    // 数据类型，如 xsd:string
                    rangeRes = model.createResource(request.getRange());
                }
                prop.addProperty(RDFS.range, rangeRes);
            }
            
            // 设置描述
            if (request.getDescription() != null && !request.getDescription().isEmpty()) {
                prop.addProperty(RDFS.comment, request.getDescription());
            }
            
            dataset.commit();
            log.info("Created property: {} in graph: {}", propertyUri, namedGraphUri);
            return propertyUri;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to create property", e);
            throw new RuntimeException("Failed to create property: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean updateProperty(String namedGraphUri, String propertyUri, 
                                  com.example.tdproject.ontology.dto.PropertyCreateRequest request) {
        if (namedGraphUri == null || propertyUri == null || request == null) {
            return false;
        }
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return false;
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            Property prop = model.createProperty(propertyUri);
            
            if (!model.containsResource(prop)) {
                dataset.commit();
                return false;
            }
            
            // 移除旧的label、comment、domain、range
            model.removeAll(prop, RDFS.label, null);
            model.removeAll(prop, RDFS.comment, null);
            model.removeAll(prop, RDFS.domain, null);
            model.removeAll(prop, RDFS.range, null);
            
            // 更新label
            if (request.getName() != null && !request.getName().isEmpty()) {
                prop.addProperty(RDFS.label, request.getName());
            }
            
            // 更新描述
            if (request.getDescription() != null) {
                if (!request.getDescription().isEmpty()) {
                    prop.addProperty(RDFS.comment, request.getDescription());
                }
            }
            
            // 更新定义域
            if (request.getDomain() != null && !request.getDomain().isEmpty()) {
                Resource domainRes = model.createResource(request.getDomain());
                prop.addProperty(RDFS.domain, domainRes);
            }
            
            // 更新值域
            if (request.getRange() != null && !request.getRange().isEmpty()) {
                Resource rangeRes = model.createResource(request.getRange());
                prop.addProperty(RDFS.range, rangeRes);
            }
            
            dataset.commit();
            log.info("Updated property: {} in graph: {}", propertyUri, namedGraphUri);
            return true;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to update property", e);
            return false;
        }
    }
    
    @Override
    public boolean deleteProperty(String namedGraphUri, String propertyUri) {
        if (namedGraphUri == null || propertyUri == null) {
            return false;
        }
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                dataset.commit();
                return false;
            }
            
            Model model = dataset.getNamedModel(namedGraphUri);
            Property prop = model.createProperty(propertyUri);
            
            if (!model.containsResource(prop)) {
                dataset.commit();
                return false;
            }
            
            // 删除该属性的所有陈述
            model.removeAll(prop, null, null);
            model.removeAll(null, prop, null);
            
            dataset.commit();
            log.info("Deleted property: {} from graph: {}", propertyUri, namedGraphUri);
            return true;
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to delete property", e);
            return false;
        }
    }
    
    // =====================================================================
    // 图谱可视化的规模上限
    //
    // 【背景】本图谱的命名图里有 356 万条三元组、约 196 万个「货物」实例。
    // 改造前的实现在这数据上是这样坏的（实测）：
    //   · 节点恒为空 —— 它用 `?x a owl:Class` 与 `?x a owl:NamedIndividual`
    //     取节点，而这批数据的类型断言是自定义的 `vocab#货物`，两条查询都是空集；
    //   · 边无上限全图扫描 —— 产出 1,459,728 条边、响应体 302 MB；
    //   · 边两端 ID 由 localName 兜底拼出，节点集合里根本没有 → 全部悬空。
    // 所以改成「类型聚合 + 抽样」：节点与边都有硬上限，边只连在已选中节点之间，
    // 从而不产生悬空边；被截断的事实通过 statistics 如实告诉前端。
    // =====================================================================

    /** 类节点上限（按实例数降序取前 N 个类型） */
    private static final int MAX_TYPES = 50;
    /** 每个类型抽样多少个实例 */
    private static final int PER_TYPE_LIMIT = 100;
    /** 只为实例数最多的前 N 个类型抽样实例，避免总节点数失控 */
    private static final int MAX_SAMPLED_TYPES = 8;
    /** 节点总数上限（含为连通性补进来的对端节点） */
    private static final int NODE_LIMIT = 600;
    /** 边数上限 */
    private static final int EDGE_LIMIT = 5000;
    /** 类型聚合查询的行数上限 */
    private static final int TYPE_ROW_LIMIT = 2000;
    /** 单条查询超时（毫秒） */
    private static final long QUERY_TIMEOUT_MS = 30_000L;
    /**
     * 实例列表查询的行数上限。
     *
     * <p>本图约有 196 万实例（`vocab#货物` 一种类型就占 196 万）。原实现无 LIMIT、
     * 且对每条实例写一行 INFO 日志 —— 实测一次 `/ontology/{id}/stats` 调用会把
     * 日志文件写掉几百 MB 并长时间不返回，连带拖垮后端。这里限流到 5000：
     * 详情页只需要「看得到实例」，总量由 COUNT 查询单独给出（见
     * {@link #getIndividualCountByClass} 传 null 的分支）。
     */
    private static final int MAX_INDIVIDUALS = 5000;

    /**
     * 把 URI 安全地写成 SPARQL 的 IRI 记号 {@code <...>}。
     *
     * <p>不能用 {@code FmtUtils.stringForNode()}：它会做**前缀缩写**，
     * 把 {@code http://www.w3.org/1999/02/22-rdf-syntax-ns#type} 缩写成
     * {@code rdf:type}，而这里拼出来的查询没有 PREFIX 声明，Jena 会直接报
     * "Unresolved prefixed name: rdf:type"（实测踩过）。
     * 所以强制输出完整 {@code <...>}，并按 SPARQL IRIREF 规则把不允许的字符
     * 转成 {@code \\uXXXX}。
     */
    private static String iri(String uri) {
        StringBuilder sb = new StringBuilder(uri.length() + 2);
        sb.append('<');
        for (int i = 0; i < uri.length(); i++) {
            char c = uri.charAt(i);
            if (c <= 0x20 || c == 0x7f || "<>\"{}|^`\\".indexOf(c) >= 0) {
                sb.append(String.format("\\u%04X", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('>');
        return sb.toString();
    }

    /** 把字符串安全地写成 SPARQL 字面量 */
    private static String literal(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 拼 VALUES 子句里的 IRI 列表 */
    private static String valuesList(java.util.Collection<String> uris) {
        StringBuilder sb = new StringBuilder();
        for (String u : uris) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(iri(u));
        }
        return sb.toString();
    }

    /** 取 URI 最后一个 / 或 # 之后的部分作为显示名，过长则截断 */
    private static String shortName(String uri) {
        if (uri == null) {
            return "";
        }
        int i = Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('#'));
        String s = (i >= 0 && i + 1 < uri.length()) ? uri.substring(i + 1) : uri;
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    /** 生成唯一节点 ID（同名不同 URI 时追加序号，避免前端按 id 索引时互相覆盖） */
    private static String uniqueId(String prefix, String base, Set<String> usedIds) {
        String safe = (base == null || base.isEmpty()) ? "node" : base.replaceAll("\\s+", "_");
        String id = prefix + safe;
        int n = 2;
        while (!usedIds.add(id)) {
            id = prefix + safe + "_" + n++;
        }
        return id;
    }

    @Override
    public OntologyVisualizationDTO getVisualizationData(String namedGraphUri) {
        List<OntologyVisualizationDTO.NodeDTO> nodes = new ArrayList<>();
        List<OntologyVisualizationDTO.EdgeDTO> edges = new ArrayList<>();
        Map<String, Object> statistics = new LinkedHashMap<>();

        if (namedGraphUri == null) {
            return OntologyVisualizationDTO.builder()
                    .nodes(nodes).edges(edges).statistics(statistics).build();
        }

        log.info("getVisualizationData: querying graph: {}", namedGraphUri);
        String graphTerm = iri(namedGraphUri);

        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(namedGraphUri)) {
                log.warn("getVisualizationData: 命名图不存在，返回空: {}", namedGraphUri);
                dataset.commit();
                statistics.put("graphExists", false);
                return OntologyVisualizationDTO.builder()
                        .nodes(nodes).edges(edges).statistics(statistics).build();
            }

            // ── 0. 真实总量：抽样归抽样，真实规模必须如实告诉前端 ──
            // 一次扫描同时取「三元组数」与「不同主语数」。
            // 实测（TDB1，356 万三元组）：单跑 COUNT(*) 4.1s、单跑
            // COUNT(DISTINCT ?s) 9.5s，拆成两条等于把整图扫两遍——合并后省掉一遍。
            //
            // 注意这里不再单独跑「?s a ?t 的不同主语数」：那是第三次全图扫描。
            // 类型维度的数量由下面的 typeDistribution 给出（按类型分别计数），
            // 键名用 totalSubjects 而不是 totalInstances，避免与旧口径混淆。
            long totalTriples = 0L;
            long totalSubjects = 0L;
            String totalsQuery =
                    "SELECT (COUNT(*) AS ?c) (COUNT(DISTINCT ?s) AS ?d) WHERE { " +
                    "  GRAPH " + graphTerm + " { ?s ?p ?o } " +
                    "}";
            Query qTotals = QueryFactory.create(totalsQuery);
            try (QueryExecution qe = QueryExecutionFactory.create(qTotals, dataset)) {
                qe.setTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                ResultSet rs = qe.execSelect();
                if (rs.hasNext()) {
                    QuerySolution sol = rs.nextSolution();
                    RDFNode c = sol.get("c");
                    RDFNode d = sol.get("d");
                    if (c != null && c.isLiteral()) {
                        totalTriples = c.asLiteral().getLong();
                    }
                    if (d != null && d.isLiteral()) {
                        totalSubjects = d.asLiteral().getLong();
                    }
                }
            }

            // ── 1. 类型聚合 ──
            // 先去重出「每个类型 URI 的实例数」，再在 Java 侧按 localName 归并。
            // 为什么不直接在 SPARQL 里 GROUP BY 完整 URI：这批数据 277 个类型 URI
            // 里有 275 个的 localName 都是「货物」（每个源文件各占一个命名空间），
            // 按完整 URI 分组会得到 275 个信息量为零的重复节点。
            Map<String, Long> typeCount = new LinkedHashMap<>();
            Map<String, List<String>> typeUris = new LinkedHashMap<>();
            String typeQuery =
                    "SELECT ?type (COUNT(?s) AS ?cnt) WHERE { " +
                    "  GRAPH " + graphTerm + " { ?s a ?type } " +
                    "  FILTER (isIRI(?type)) " +
                    "} GROUP BY ?type ORDER BY DESC(?cnt) LIMIT " + TYPE_ROW_LIMIT;
            Query q1 = QueryFactory.create(typeQuery);
            try (QueryExecution qe = QueryExecutionFactory.create(q1, dataset)) {
                qe.setTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                ResultSet rs = qe.execSelect();
                while (rs.hasNext()) {
                    QuerySolution sol = rs.nextSolution();
                    String typeUri = sol.getResource("type").getURI();
                    long cnt = sol.getLiteral("cnt").getLong();
                    String shortType = shortName(typeUri);
                    typeCount.merge(shortType, cnt, Long::sum);
                    typeUris.computeIfAbsent(shortType, k -> new ArrayList<>()).add(typeUri);
                }
            }

            List<String> topTypes = new ArrayList<>(typeCount.keySet());
            topTypes.sort((a, b) -> Long.compare(typeCount.get(b), typeCount.get(a)));
            boolean truncated = typeCount.size() > MAX_TYPES;
            if (topTypes.size() > MAX_TYPES) {
                topTypes = new ArrayList<>(topTypes.subList(0, MAX_TYPES));
            }

            Set<String> usedIds = new LinkedHashSet<>();
            Map<String, String> typeNodeId = new LinkedHashMap<>();
            for (String tn : topTypes) {
                long cnt = typeCount.get(tn);
                String nodeId = uniqueId("class_", tn, usedIds);
                typeNodeId.put(tn, nodeId);
                List<String> urisOfType = typeUris.get(tn);
                nodes.add(OntologyVisualizationDTO.NodeDTO.builder()
                        .id(nodeId)
                        .label(tn + "（" + cnt + "）")
                        .type("class")
                        .color("#1890ff")   // 蓝色-类
                        .size(60)
                        .level(0)
                        .uri(urisOfType == null || urisOfType.isEmpty() ? null : urisOfType.get(0))
                        .build());
            }

            // ── 2. 为实例数最多的前 MAX_SAMPLED_TYPES 个类型抽样实例 ──
            // 按类型 localName 匹配（而不是某个具体类型 URI），这样抽样覆盖整个
            // 数据集而不只是一两个源文件。
            Set<String> seedUris = new LinkedHashSet<>();
            int sampledTypes = 0;
            for (String tn : topTypes) {
                if (sampledTypes >= MAX_SAMPLED_TYPES || seedUris.size() >= NODE_LIMIT / 2) {
                    break;
                }
                String q =
                        "SELECT ?s WHERE { " +
                        "  GRAPH " + graphTerm + " { ?s a ?t } " +
                        "  FILTER (REPLACE(STR(?t), '^.*[/#]', '') = " + literal(tn) + ") " +
                        "} LIMIT " + PER_TYPE_LIMIT;
                Query qq = QueryFactory.create(q);
                try (QueryExecution qe = QueryExecutionFactory.create(qq, dataset)) {
                    qe.setTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    ResultSet rs = qe.execSelect();
                    while (rs.hasNext()) {
                        Resource r = rs.nextSolution().getResource("s");
                        if (r != null && r.getURI() != null) {
                            seedUris.add(r.getURI());
                        }
                    }
                }
                sampledTypes++;
            }

            // ── 3. 补一层「种子节点一步可达的对端节点」──
            // 否则边几乎全被过滤掉：这批数据是 货物 --仓储位置--> 仓库/省市，
            // 种子全是「货物」，只取两端都在种子集合内的边会得到空集。
            Set<String> selected = new LinkedHashSet<>(seedUris);
            if (!seedUris.isEmpty() && selected.size() < NODE_LIMIT) {
                String q =
                        "SELECT DISTINCT ?o WHERE { " +
                        "  GRAPH " + graphTerm + " { ?s ?p ?o } " +
                        "  FILTER (isIRI(?o)) " +
                        "  FILTER (?p != " + iri(RDF.type.getURI()) + ") " +
                        "  FILTER (?p != " + iri(RDFS.subClassOf.getURI()) + ") " +
                        "  VALUES ?s { " + valuesList(seedUris) + " } " +
                        "} LIMIT " + (NODE_LIMIT - selected.size());
                Query qq = QueryFactory.create(q);
                try (QueryExecution qe = QueryExecutionFactory.create(qq, dataset)) {
                    qe.setTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    ResultSet rs = qe.execSelect();
                    while (rs.hasNext()) {
                        Resource r = rs.nextSolution().getResource("o");
                        if (r != null && r.getURI() != null && selected.size() < NODE_LIMIT) {
                            selected.add(r.getURI());
                        }
                    }
                }
            }
            if (selected.size() >= NODE_LIMIT) {
                truncated = true;
            }

            // 为选中节点建节点，同时建立 URI -> 节点ID 映射
            Map<String, String> uriToId = new LinkedHashMap<>();
            for (String uri : selected) {
                String nodeId = uniqueId("ind_", shortName(uri), usedIds);
                uriToId.put(uri, nodeId);
                nodes.add(OntologyVisualizationDTO.NodeDTO.builder()
                        .id(nodeId)
                        .label(shortName(uri))
                        .type("individual")
                        .color("#52c41a")   // 绿色-实例
                        .size(40)
                        .level(1)
                        .uri(uri)
                        .build());
            }

            // ── 4. 只在选中节点之间取边（两端都必须在 uriToId 里，故不会悬空）──
            if (uriToId.size() >= 2) {
                String vals = valuesList(uriToId.keySet());
                String q =
                        "SELECT ?s ?p ?o WHERE { " +
                        "  GRAPH " + graphTerm + " { ?s ?p ?o } " +
                        "  FILTER (isIRI(?o)) " +
                        "  FILTER (?p != " + iri(RDF.type.getURI()) + ") " +
                        "  FILTER (?p != " + iri(RDFS.subClassOf.getURI()) + ") " +
                        "  VALUES ?s { " + vals + " } " +
                        "  VALUES ?o { " + vals + " } " +
                        "} LIMIT " + EDGE_LIMIT;
                Query qq = QueryFactory.create(q);
                int seq = 0;
                try (QueryExecution qe = QueryExecutionFactory.create(qq, dataset)) {
                    qe.setTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    ResultSet rs = qe.execSelect();
                    while (rs.hasNext()) {
                        QuerySolution sol = rs.nextSolution();
                        Resource sRes = sol.getResource("s");
                        Resource oRes = sol.getResource("o");
                        Resource pRes = sol.getResource("p");
                        String sid = sRes == null ? null : uriToId.get(sRes.getURI());
                        String tid = oRes == null ? null : uriToId.get(oRes.getURI());
                        if (sid == null || tid == null) {
                            continue;   // 双保险：不产生悬空边
                        }
                        edges.add(OntologyVisualizationDTO.EdgeDTO.builder()
                                .id("edge_rel_" + (seq++))
                                .source(sid)
                                .target(tid)
                                .label(pRes == null ? "" : shortName(pRes.getURI()))
                                .type("objectProperty")
                                .color("#fa8c16")
                                .build());
                    }
                }
                if (edges.size() >= EDGE_LIMIT) {
                    truncated = true;
                }
            }

            // ── 5. 实例 -> 类型 的 instanceOf 边，把节点挂到类下便于前端分层 ──
            if (!uriToId.isEmpty() && !typeNodeId.isEmpty()) {
                String q =
                        "SELECT ?s ?t WHERE { " +
                        "  GRAPH " + graphTerm + " { ?s a ?t } " +
                        "  VALUES ?s { " + valuesList(uriToId.keySet()) + " } " +
                        "}";
                Query qq = QueryFactory.create(q);
                int seq = 0;
                try (QueryExecution qe = QueryExecutionFactory.create(qq, dataset)) {
                    qe.setTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    ResultSet rs = qe.execSelect();
                    while (rs.hasNext()) {
                        QuerySolution sol = rs.nextSolution();
                        Resource sRes = sol.getResource("s");
                        Resource tRes = sol.getResource("t");
                        if (sRes == null || tRes == null) {
                            continue;
                        }
                        String sid = uriToId.get(sRes.getURI());
                        String tid = typeNodeId.get(shortName(tRes.getURI()));
                        if (sid == null || tid == null) {
                            continue;
                        }
                        edges.add(OntologyVisualizationDTO.EdgeDTO.builder()
                                .id("edge_type_" + (seq++))
                                .source(sid)
                                .target(tid)
                                .label("instanceOf")
                                .type("instanceOf")
                                .color("#52c41a")
                                .build());
                    }
                }
            }

            dataset.commit();

            // ── 6. 如实汇报：返回的是抽样，真实规模在这里 ──
            Map<String, Object> typeDistribution = new LinkedHashMap<>();
            for (String tn : topTypes) {
                typeDistribution.put(tn, typeCount.get(tn));
            }
            // 按类型分别计数的实例数之和。对每个实例只有一个类型的数据等于实例总数；
            // 多类型实例会被重复计入，这是「类型维度」的计数而非去重后的实例数。
            long typedInstanceSum = 0L;
            for (Long v : typeCount.values()) {
                typedInstanceSum += (v == null ? 0L : v);
            }

            statistics.put("graphExists", true);
            statistics.put("totalTriples", totalTriples);
            statistics.put("totalSubjects", totalSubjects);
            statistics.put("typedInstanceSum", typedInstanceSum);
            statistics.put("typeCount", typeCount.size());
            statistics.put("returnedNodes", nodes.size());
            statistics.put("returnedEdges", edges.size());
            statistics.put("sampled", true);
            statistics.put("truncated", truncated);
            statistics.put("nodeLimit", NODE_LIMIT);
            statistics.put("edgeLimit", EDGE_LIMIT);
            statistics.put("typeDistribution", typeDistribution);

            log.info("getVisualizationData: graph={} 三元组={} 主语={} 类型={} -> "
                            + "返回 {} 节点 / {} 边 (truncated={})",
                    namedGraphUri, totalTriples, totalSubjects, typeCount.size(),
                    nodes.size(), edges.size(), truncated);

        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to get visualization data", e);
            statistics.put("error", String.valueOf(e.getMessage()));
        }

        return OntologyVisualizationDTO.builder()
                .nodes(nodes)
                .edges(edges)
                .statistics(statistics)
                .build();
    }

    @Override
    public void saveVisualizationData(String namedGraphUri, 
                                      List<OntologyVisualizationDTO.NodeDTO> nodes, 
                                      List<OntologyVisualizationDTO.EdgeDTO> edges) {
        if (namedGraphUri == null || namedGraphUri.isEmpty()) {
            log.error("saveVisualizationData: namedGraphUri is null or empty");
            throw new IllegalArgumentException("命名图URI不能为空");
        }
        
        log.info("saveVisualizationData: saving {} nodes and {} edges to {}", 
                nodes != null ? nodes.size() : 0, 
                edges != null ? edges.size() : 0, 
                namedGraphUri);
        
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            // 1. 如果命名图已存在，先删除
            if (dataset.containsNamedModel(namedGraphUri)) {
                log.info("saveVisualizationData: removing existing graph: {}", namedGraphUri);
                dataset.removeNamedModel(namedGraphUri);
            }
            
            // 2. 创建新的命名图模型
            Model model = ModelFactory.createDefaultModel();
            
            // 设置命名空间前缀
            String baseUri = namedGraphUri.endsWith("/") ? namedGraphUri : namedGraphUri + "/";
            model.setNsPrefix("", baseUri);
            model.setNsPrefix("owl", OWL.NS);
            model.setNsPrefix("rdf", RDF.getURI());
            model.setNsPrefix("rdfs", RDFS.getURI());
            
            // 3. 创建类定义（根据节点类型）
            if (nodes != null) {
                for (OntologyVisualizationDTO.NodeDTO node : nodes) {
                    if ("class".equals(node.getType())) {
                        // 创建类 - 强制使用 baseUri 构建
                        String classUri = baseUri + node.getId();
                        Resource classRes = model.createResource(classUri);
                        classRes.addProperty(RDF.type, OWL.Class);
                        if (node.getLabel() != null) {
                            classRes.addProperty(RDFS.label, node.getLabel());
                        }
                    }
                }
                
                // 4. 创建实例
                for (OntologyVisualizationDTO.NodeDTO node : nodes) {
                    if ("individual".equals(node.getType())) {
                        // 强制使用 baseUri 构建 URI
                        String indUri = baseUri + node.getId();
                        Resource indRes = model.createResource(indUri);
                        indRes.addProperty(RDF.type, OWL2.NamedIndividual);
                        if (node.getLabel() != null) {
                            indRes.addProperty(RDFS.label, node.getLabel());
                        }
                    }
                }
            }
            
            // 5. 创建对象属性（边关系）
            if (edges != null) {
                // 首先收集所有关系类型
                Map<String, Property> propertyMap = new HashMap<>();
                
                for (OntologyVisualizationDTO.EdgeDTO edge : edges) {
                    String propUri = baseUri + "property/" + edge.getType();
                    Property property = propertyMap.computeIfAbsent(propUri, model::createProperty);
                    
                    // 强制使用 baseUri + ID 构建 URI
                    String sourceUri = baseUri + edge.getSource();
                    String targetUri = baseUri + edge.getTarget();
                    
                    // 创建关系三元组
                    Resource sourceRes = model.createResource(sourceUri);
                    Resource targetRes = model.createResource(targetUri);
                    sourceRes.addProperty(property, targetRes);
                }
            }
            
            // 6. 将模型添加到数据集
            dataset.addNamedModel(namedGraphUri, model);
            dataset.commit();
            
            log.info("saveVisualizationData: successfully saved data to {}", namedGraphUri);
            
        } catch (Exception e) {
            dataset.abort();
            log.error("Failed to save visualization data", e);
            throw new RuntimeException("保存可视化数据失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Dataset getDataset() {
        return dataset;
    }
}