package com.example.tdproject;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb.TDBFactory;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * RDF 追加导入测试
 * 先导入框架(trade.rdf)，再追加数据(哈萨克斯坦.rdf)
 */
public class RdfAppendTest {
    
    private static final String TDB_PATH = "runtime/ontology-graph-store";
    private static final String NAMED_GRAPH = "http://example.org/ontology/trade-combined/1.0";

    /** 项目工作区根目录：由 Maven surefire 注入，默认按模块目录上溯两级（=项目根） */
    private static final String WORKSPACE =
            System.getProperty("trade.workspace", "../..");
    private static final String SCHEMA_FILE = WORKSPACE + "/deliverables/本体与映射规则/trade（protege建模导出）.rdf";
    private static final String DATA_FILE = WORKSPACE + "/data/02_派生_贸易知识图谱RDF/2015_3_4_Kazakhstan_exit_mapped_data.rdf";
    
    @Test
    public void testAppendImport() throws Exception {
        Dataset dataset = TDBFactory.createDataset(TDB_PATH);
        
        try {
            dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
            
            // 第1步：导入框架（如果不存在则创建）
            System.out.println("=== 第1步：导入本体框架 ===");
            Model combinedModel;
            if (dataset.containsNamedModel(NAMED_GRAPH)) {
                // 如果已存在，读取已有模型
                combinedModel = dataset.getNamedModel(NAMED_GRAPH);
                System.out.println("已有数据，当前三元组数: " + combinedModel.size());
            } else {
                // 如果不存在，创建新模型
                combinedModel = ModelFactory.createDefaultModel();
                System.out.println("新建模型");
            }
            
            // 读取 trade.rdf
            try (InputStream is = new FileInputStream(SCHEMA_FILE)) {
                Model schemaModel = ModelFactory.createDefaultModel();
                RDFDataMgr.read(schemaModel, is, org.apache.jena.riot.Lang.RDFXML);
                combinedModel.add(schemaModel);
                System.out.println("添加框架后三元组数: " + combinedModel.size());
            }
            
            // 第2步：追加数据
            System.out.println("\n=== 第2步：追加实例数据 ===");
            try (InputStream is = new FileInputStream(DATA_FILE)) {
                Model dataModel = ModelFactory.createDefaultModel();
                RDFDataMgr.read(dataModel, is, org.apache.jena.riot.Lang.RDFXML);
                combinedModel.add(dataModel);
                System.out.println("添加数据后三元组数: " + combinedModel.size());
            }
            
            // 保存到命名图
            dataset.removeNamedModel(NAMED_GRAPH);
            dataset.addNamedModel(NAMED_GRAPH, combinedModel);
            dataset.commit();
            
            System.out.println("\n=== 导入完成 ===");
            System.out.println("命名图: " + NAMED_GRAPH);
            System.out.println("总三元组数: " + combinedModel.size());
            
        } catch (Exception e) {
            dataset.abort();
            throw e;
        } finally {
            dataset.close();
        }
    }
}
