package com.example.tdproject;

import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.OWL;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * 本体一致性检查
 * 验证 RDF 数据文件是否和本体框架（trade.rdf）一致
 */
public class OntologyConsistencyTest {
    
    /** 项目工作区根目录；可用 -Dtrade.workspace=... 覆盖 */
    private static final String WORKSPACE =
            System.getProperty("trade.workspace", "../..");

    // 本体框架路径
    private static final String SCHEMA_FILE = WORKSPACE + "/deliverables/本体与映射规则/trade（protege建模导出）.rdf";
    // RDF 数据文件夹（mapped_data.rdf 全量）
    private static final String DATA_DIR = WORKSPACE + "/data/02_派生_贸易知识图谱RDF";
    
    /**
     * 检查单个数据文件与框架的一致性
     */
    @Test
    public void testSingleFileConsistency() throws Exception {
        System.out.println("=== 检查单个文件与本体框架的一致性 ===\n");
        
        // 1. 读取本体框架
        Set<String> schemaClasses = new HashSet<>();
        Set<String> schemaObjectProps = new HashSet<>();
        Set<String> schemaDatatypeProps = new HashSet<>();
        
        try (InputStream is = new FileInputStream(SCHEMA_FILE)) {
            Model schemaModel = ModelFactory.createDefaultModel();
            RDFDataMgr.read(schemaModel, is, org.apache.jena.riot.Lang.RDFXML);
            
            // 提取类（owl:Class 或 rdfs:Class）
            ResIterator classIter = schemaModel.listResourcesWithProperty(RDF.type, OWL.Class);
            while (classIter.hasNext()) {
                Resource cls = classIter.nextResource();
                schemaClasses.add(cls.getURI());
            }
            // 也检查 rdfs:Class
            classIter = schemaModel.listResourcesWithProperty(RDF.type, RDFS.Class);
            while (classIter.hasNext()) {
                Resource cls = classIter.nextResource();
                schemaClasses.add(cls.getURI());
            }
            
            // 提取对象属性
            ResIterator objPropIter = schemaModel.listResourcesWithProperty(RDF.type, OWL.ObjectProperty);
            while (objPropIter.hasNext()) {
                Resource prop = objPropIter.nextResource();
                schemaObjectProps.add(prop.getURI());
            }
            
            // 提取数据属性
            ResIterator dataPropIter = schemaModel.listResourcesWithProperty(RDF.type, OWL.DatatypeProperty);
            while (dataPropIter.hasNext()) {
                Resource prop = dataPropIter.nextResource();
                schemaDatatypeProps.add(prop.getURI());
            }
        }
        
        System.out.println("【本体框架定义】");
        System.out.println("类数量: " + schemaClasses.size());
        schemaClasses.forEach(c -> System.out.println("  - " + c));
        System.out.println("对象属性: " + schemaObjectProps.size());
        schemaObjectProps.forEach(p -> System.out.println("  - " + p));
        System.out.println("数据属性: " + schemaDatatypeProps.size());
        schemaDatatypeProps.forEach(p -> System.out.println("  - " + p));
        
        // 2. 读取数据文件
        String dataFile = DATA_DIR + "/2015_3_4_Kazakhstan_exit_mapped_data.rdf";
        Model dataModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(dataModel, dataFile);
        
        System.out.println("\n【数据文件分析: " + new File(dataFile).getName() + "】");
        System.out.println("三元组总数: " + dataModel.size());
        
        // 提取数据文件中使用的类
        Set<String> dataClasses = new HashSet<>();
        ResIterator typeIter = dataModel.listResourcesWithProperty(RDF.type);
        while (typeIter.hasNext()) {
            Resource r = typeIter.nextResource();
            Statement typeStmt = r.getProperty(RDF.type);
            if (typeStmt != null && typeStmt.getObject().isResource()) {
                String typeUri = typeStmt.getResource().getURI();
                if (typeUri != null) {
                    dataClasses.add(typeUri);
                }
            }
        }
        
        // 提取数据文件中使用的属性
        Set<String> dataProperties = new HashSet<>();
        StmtIterator stmtIter = dataModel.listStatements();
        while (stmtIter.hasNext()) {
            Statement stmt = stmtIter.nextStatement();
            Property prop = stmt.getPredicate();
            // 跳过 rdf:type 和系统属性
            if (!prop.equals(RDF.type) && !prop.getURI().startsWith("http://www.w3.org/")) {
                dataProperties.add(prop.getURI());
            }
        }
        
        System.out.println("使用的类: " + dataClasses.size());
        dataClasses.forEach(c -> {
            boolean inSchema = schemaClasses.contains(c);
            System.out.println("  - " + c + (inSchema ? " ✓" : " ✗ (未在框架中定义)"));
        });
        
        System.out.println("使用的属性: " + dataProperties.size());
        dataProperties.forEach(p -> {
            boolean inSchema = schemaObjectProps.contains(p) || schemaDatatypeProps.contains(p);
            System.out.println("  - " + p + (inSchema ? " ✓" : " ✗ (未在框架中定义)"));
        });
        
        // 3. 对比结果
        System.out.println("\n【一致性检查结果】");
        Set<String> undefinedClasses = new HashSet<>(dataClasses);
        undefinedClasses.removeAll(schemaClasses);
        
        Set<String> undefinedProps = new HashSet<>(dataProperties);
        undefinedProps.removeAll(schemaObjectProps);
        undefinedProps.removeAll(schemaDatatypeProps);
        
        if (undefinedClasses.isEmpty() && undefinedProps.isEmpty()) {
            System.out.println("✓ 完全一致！数据文件符合本体框架定义。");
        } else {
            System.out.println("✗ 发现不一致：");
            if (!undefinedClasses.isEmpty()) {
                System.out.println("  未定义的类:");
                undefinedClasses.forEach(c -> System.out.println("    - " + c));
            }
            if (!undefinedProps.isEmpty()) {
                System.out.println("  未定义的属性:");
                undefinedProps.forEach(p -> System.out.println("    - " + p));
            }
        }
    }
    
    /**
     * 批量检查所有 RDF 文件
     */
    @Test
    public void testAllFilesConsistency() throws Exception {
        System.out.println("=== 批量检查所有 RDF 文件 ===\n");
        
        // 1. 读取本体框架
        Set<String> schemaClasses = new HashSet<>();
        Set<String> schemaProperties = new HashSet<>();
        
        try (InputStream is = new FileInputStream(SCHEMA_FILE)) {
            Model schemaModel = ModelFactory.createDefaultModel();
            RDFDataMgr.read(schemaModel, is, org.apache.jena.riot.Lang.RDFXML);
            
            // 提取类
            ResIterator classIter = schemaModel.listResourcesWithProperty(RDF.type, OWL.Class);
            while (classIter.hasNext()) {
                schemaClasses.add(classIter.nextResource().getURI());
            }
            classIter = schemaModel.listResourcesWithProperty(RDF.type, RDFS.Class);
            while (classIter.hasNext()) {
                schemaClasses.add(classIter.nextResource().getURI());
            }
            
            // 提取属性（对象属性 + 数据属性）
            ResIterator objPropIter = schemaModel.listResourcesWithProperty(RDF.type, OWL.ObjectProperty);
            while (objPropIter.hasNext()) {
                schemaProperties.add(objPropIter.nextResource().getURI());
            }
            ResIterator dataPropIter = schemaModel.listResourcesWithProperty(RDF.type, OWL.DatatypeProperty);
            while (dataPropIter.hasNext()) {
                schemaProperties.add(dataPropIter.nextResource().getURI());
            }
        }
        
        System.out.println("【本体框架】");
        System.out.println("定义了 " + schemaClasses.size() + " 个类，" + schemaProperties.size() + " 个属性\n");
        
        // 2. 扫描所有 RDF 文件
        File dir = new File(DATA_DIR);
        File[] rdfFiles = dir.listFiles((d, name) -> name.endsWith(".rdf") && !name.contains("trade"));
        
        if (rdfFiles == null || rdfFiles.length == 0) {
            System.out.println("未找到数据文件");
            return;
        }
        
        System.out.println("【扫描文件】共 " + rdfFiles.length + " 个 RDF 文件\n");
        
        // 统计信息
        int consistentFiles = 0;
        int inconsistentFiles = 0;
        Set<String> allUndefinedClasses = new HashSet<>();
        Set<String> allUndefinedProperties = new HashSet<>();
        Map<String, Set<String>> fileIssues = new HashMap<>();
        
        for (File file : rdfFiles) {
            try {
                Model model = ModelFactory.createDefaultModel();
                RDFDataMgr.read(model, file.getAbsolutePath());
                
                // 提取使用的类
                Set<String> usedClasses = new HashSet<>();
                ResIterator typeIter = model.listResourcesWithProperty(RDF.type);
                while (typeIter.hasNext()) {
                    Resource r = typeIter.nextResource();
                    Statement stmt = r.getProperty(RDF.type);
                    if (stmt != null && stmt.getObject().isResource()) {
                        usedClasses.add(stmt.getResource().getURI());
                    }
                }
                
                // 提取使用的属性
                Set<String> usedProps = new HashSet<>();
                StmtIterator stmtIter = model.listStatements();
                while (stmtIter.hasNext()) {
                    Property p = stmtIter.nextStatement().getPredicate();
                    if (!p.equals(RDF.type) && !p.getURI().startsWith("http://www.w3.org/")) {
                        usedProps.add(p.getURI());
                    }
                }
                
                // 检查是否一致
                Set<String> undefinedCls = new HashSet<>(usedClasses);
                undefinedCls.removeAll(schemaClasses);
                
                Set<String> undefinedProp = new HashSet<>(usedProps);
                undefinedProp.removeAll(schemaProperties);
                
                if (undefinedCls.isEmpty() && undefinedProp.isEmpty()) {
                    consistentFiles++;
                    System.out.println("✓ " + file.getName() + " (" + model.size() + " 三元组)");
                } else {
                    inconsistentFiles++;
                    System.out.println("✗ " + file.getName() + " (" + model.size() + " 三元组)");
                    if (!undefinedCls.isEmpty()) {
                        System.out.println("   未定义类: " + undefinedCls);
                        allUndefinedClasses.addAll(undefinedCls);
                    }
                    if (!undefinedProp.isEmpty()) {
                        System.out.println("   未定义属性: " + undefinedProp);
                        allUndefinedProperties.addAll(undefinedProp);
                    }
                    fileIssues.put(file.getName(), new HashSet<>(){{
                        addAll(undefinedCls); addAll(undefinedProp);
                    }});
                }
                
                model.close();
            } catch (Exception e) {
                System.out.println("✗ " + file.getName() + " (解析错误: " + e.getMessage() + ")");
            }
        }
        
        // 3. 总结报告
        System.out.println("\n========== 一致性检查总结 ==========");
        System.out.println("总文件数: " + rdfFiles.length);
        System.out.println("✓ 一致的文件: " + consistentFiles);
        System.out.println("✗ 不一致的文件: " + inconsistentFiles);
        
        if (!allUndefinedClasses.isEmpty()) {
            System.out.println("\n【发现的未定义类】");
            allUndefinedClasses.forEach(c -> System.out.println("  - " + c));
        }
        
        if (!allUndefinedProperties.isEmpty()) {
            System.out.println("\n【发现的未定义属性】");
            allUndefinedProperties.forEach(p -> {
                // 简化显示：只显示本地名
                String shortName = p.contains("#") ? p.substring(p.lastIndexOf("#") + 1) : p;
                System.out.println("  - " + shortName + " (" + p + ")");
            });
        }
        
        if (!fileIssues.isEmpty()) {
            System.out.println("\n【不一致文件列表】");
            fileIssues.forEach((file, issues) -> {
                System.out.println("  " + file + ": " + issues.size() + " 个问题");
            });
        }
    }
}
