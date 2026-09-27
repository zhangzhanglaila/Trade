package com.example.tdproject;

import org.apache.jena.rdf.model.*;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.query.*;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * RDF 导入测试类
 */
public class RdfImportTest {
    
    /** 项目工作区根目录：由 Maven surefire 注入，默认按模块目录上溯两级（=项目根） */
    private static final String WORKSPACE =
            System.getProperty("trade.workspace", "../..");

    // RDF 文件路径
    private static final String RDF_FILE = WORKSPACE + "/data/02_派生_贸易知识图谱RDF/2015_1_12_Tajikistan_in_mapped_data.rdf";
    
    // 图数据库存储路径（相对项目根，与 application.yaml 的 ontology.graph.store-path 保持一致）
    private static final String TDB_PATH = "runtime/ontology-graph-store";
    
    @Test
    public void testImportSingleRdf() {
        System.out.println("=== 开始导入测试 ===");
        
        // 1. 检查文件是否存在
        File file = new File(RDF_FILE);
        if (!file.exists()) {
            System.err.println("文件不存在: " + RDF_FILE);
            return;
        }
        System.out.println("文件大小: " + file.length() / 1024 + " KB");
        
        // 2. 创建内存模型（测试阶段先不写入TDB）
        Model model = ModelFactory.createDefaultModel();
        
        try {
            // 3. 读取 RDF 文件（指定UTF-8编码）
            System.out.println("正在解析 RDF 文件...");
            model.read(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8), null, "RDF/XML");
            
            // 4. 统计三元组数量
            long tripleCount = model.size();
            System.out.println("三元组数量: " + tripleCount);
            
            // 5. 查看所有不同的类型
            System.out.println("\n=== 实体类型 ===");
            StmtIterator typeIter = model.listStatements(null, RDF.type, (RDFNode) null);
            typeIter.forEachRemaining(stmt -> {
                System.out.println("类型: " + stmt.getObject());
            });
            
            // 6. 查看属性（谓词）
            System.out.println("\n=== 属性列表 ===");
            java.util.Set<Property> predicates = new java.util.HashSet<>();
            model.listStatements().forEachRemaining(stmt -> {
                predicates.add(stmt.getPredicate());
            });
            predicates.forEach(predicate -> {
                System.out.println("属性: " + predicate.getLocalName() + " -> " + predicate.getURI());
            });
            
            // 7. 查看样例数据（前5条）
            System.out.println("\n=== 样例数据（前5条）===");
            StmtIterator iter = model.listStatements();
            int count = 0;
            while (iter.hasNext() && count < 5) {
                Statement stmt = iter.next();
                System.out.println("主语: " + stmt.getSubject().getLocalName());
                System.out.println("谓语: " + stmt.getPredicate().getLocalName());
                System.out.println("宾语: " + stmt.getObject());
                System.out.println("---");
                count++;
            }
            
            // 8. 写入 TDB（可选，测试阶段可以注释掉）
            // writeToTDB(model);
            
            System.out.println("\n=== 导入测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("导入失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            model.close();
        }
    }
    
    /**
     * 写入 TDB 数据库
     */
    private void writeToTDB(Model model) {
        System.out.println("\n正在写入 TDB...");
        Dataset dataset = TDBFactory.createDataset(TDB_PATH);
        try {
            Model tdbModel = dataset.getDefaultModel();
            tdbModel.add(model);
            System.out.println("写入完成，TDB中总三元组数: " + tdbModel.size());
        } finally {
            dataset.close();
        }
    }
    
    /**
     * 查询测试（导入后验证）
     */
    @Test
    public void testQueryImportedData() {
        System.out.println("=== 查询导入的数据 ===");
        
        Dataset dataset = TDBFactory.createDataset(TDB_PATH);
        try {
            String queryStr = """
                SELECT ?subject ?predicate ?object
                WHERE {
                    ?subject ?predicate ?object .
                }
                LIMIT 10
                """;
            
            Query query = QueryFactory.create(queryStr);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
                ResultSet results = qexec.execSelect();
                while (results.hasNext()) {
                    QuerySolution soln = results.next();
                    System.out.println("主语: " + soln.get("subject"));
                    System.out.println("谓语: " + soln.get("predicate"));
                    System.out.println("宾语: " + soln.get("object"));
                    System.out.println("---");
                }
            }
        } finally {
            dataset.close();
        }
    }
}
