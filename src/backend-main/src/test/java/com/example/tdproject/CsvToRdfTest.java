package com.example.tdproject;

import com.example.tdproject.utils.CsvToRdfConverter;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.*;

/**
 * CSV 转 RDF 测试
 */
public class CsvToRdfTest {
    
    /** 项目工作区根目录；可用 -Dtrade.workspace=... 覆盖 */
    private static final String WORKSPACE =
            System.getProperty("trade.workspace", "../..");
    
    @Test
    public void testConvertSingleCsv() throws Exception {
        // 测试单个 CSV 文件转换
        String csvPath = WORKSPACE + "/data/01_原始_海关进出口明细/哈萨克斯坦/出口/2015.1—2.csv";
        
        if (!Files.exists(Paths.get(csvPath))) {
            System.out.println("CSV 文件不存在，跳过测试");
            return;
        }
        
        byte[] csvBytes = Files.readAllBytes(Paths.get(csvPath));
        byte[] rdfBytes = CsvToRdfConverter.convert(
            new ByteArrayInputStream(csvBytes),
            "KZ",      // 哈萨克斯坦代码
            "2015_01"  // 时间段
        );
        
        // 保存到文件查看
        String outputPath = "D:/test_output.rdf";
        Files.write(Paths.get(outputPath), rdfBytes);
        System.out.println("RDF 已保存到: " + outputPath);
        System.out.println("文件大小: " + rdfBytes.length + " bytes");
    }
    
    @Test
    public void testBatchConvert() throws Exception {
        // 批量转换测试
        String sourceDir = WORKSPACE + "/data/01_原始_海关进出口明细";
        String targetDir = "D:/rdf_output";
        
        if (!Files.exists(Paths.get(sourceDir))) {
            System.out.println("源目录不存在，跳过测试");
            return;
        }
        
        var result = com.example.tdproject.utils.BatchCsvToRdfConverter.batchConvert(sourceDir, targetDir);
        
        System.out.println("=== 批量转换结果 ===");
        System.out.println("成功: " + result.get("successCount"));
        System.out.println("失败: " + result.get("failedCount"));
    }
}
