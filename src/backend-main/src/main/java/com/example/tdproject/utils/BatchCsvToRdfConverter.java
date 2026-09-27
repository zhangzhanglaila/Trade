package com.example.tdproject.utils;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 批量 CSV 转 RDF 工具
 * 遍历文件夹，批量转换所有 CSV 文件
 */
public class BatchCsvToRdfConverter {
    
    /**
     * 批量转换目录下的所有 CSV 文件
     * 
     * @param sourceDir 源目录（如 D:/data/中亚国家进出口贸易数据）
     * @param targetDir 目标目录（如 D:/data/rdf_output）
     * @return 转换结果统计
     */
    public static Map<String, Object> batchConvert(String sourceDir, String targetDir) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);
        
        // 创建目标目录
        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }
        
        // 遍历所有 CSV 文件
        Files.walk(sourcePath)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
            .forEach(csvFile -> {
                try {
                    String fileName = csvFile.getFileName().toString();
                    System.out.println("[Batch] 处理: " + fileName);
                    
                    // 解析文件路径获取国家和时间段
                    // 路径示例: 哈萨克斯坦/出口/2015.1—2.csv
                    Path relativePath = sourcePath.relativize(csvFile);
                    String[] pathParts = relativePath.toString().split("[\\\\/]");
                    
                    if (pathParts.length < 3) {
                        System.out.println("[Batch] 跳过，路径格式不符: " + relativePath);
                        return;
                    }
                    
                    String countryName = pathParts[0];  // 哈萨克斯坦
                    String tradeType = pathParts[1];     // 出口
                    String periodFile = pathParts[2];    // 2015.1—2.csv
                    
                    // 国家名转代码
                    String countryCode = getCountryCode(countryName);
                    
                    // 时间段文件名转标准格式
                    String dataPeriod = parsePeriod(periodFile);
                    
                    // 读取 CSV
                    byte[] csvBytes = Files.readAllBytes(csvFile);
                    
                    // 转换为 RDF
                    byte[] rdfBytes = CsvToRdfConverter.convert(
                        new ByteArrayInputStream(csvBytes),
                        countryCode,
                        dataPeriod
                    );
                    
                    // 保存 RDF 文件
                    String rdfFileName = countryCode + "_" + tradeType + "_" + dataPeriod + ".rdf";
                    Path rdfFile = targetPath.resolve(rdfFileName);
                    Files.write(rdfFile, rdfBytes);
                    
                    System.out.println("[Batch] ✓ 成功: " + rdfFileName);
                    successFiles.add(fileName + " -> " + rdfFileName);
                    
                } catch (Exception e) {
                    System.err.println("[Batch] ✗ 失败: " + csvFile + ", 错误: " + e.getMessage());
                    failedFiles.add(csvFile.toString() + ": " + e.getMessage());
                }
            });
        
        result.put("successCount", successFiles.size());
        result.put("failedCount", failedFiles.size());
        result.put("successFiles", successFiles);
        result.put("failedFiles", failedFiles);
        
        return result;
    }
    
    /**
     * 国家名转代码
     */
    private static String getCountryCode(String countryName) {
        Map<String, String> codes = new HashMap<>();
        codes.put("哈萨克斯坦", "KZ");
        codes.put("乌兹别克斯坦", "UZ");
        codes.put("塔吉克斯坦", "TJ");
        codes.put("吉尔吉斯斯坦", "KG");
        codes.put("土库曼斯坦", "TM");
        
        return codes.getOrDefault(countryName, "UNKNOWN");
    }
    
    /**
     * 解析时间段文件名
     * 如 "2015.1—2.csv" -> "2015-Q1"
     */
    private static String parsePeriod(String fileName) {
        // 移除 .csv 后缀
        String name = fileName.replaceAll("(?i)\\.csv$", "");
        
        // 替换特殊字符为标准格式
        name = name.replace(".", "-");
        name = name.replace("—", "_");
        name = name.replace("-", "_");
        
        return name;
    }
    
    /**
     * 主方法：命令行执行批量转换
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("用法: BatchCsvToRdfConverter <源目录> <目标目录>");
            System.out.println("示例: BatchCsvToRdfConverter D:/data/csv D:/data/rdf");
            return;
        }
        
        String sourceDir = args[0];
        String targetDir = args[1];
        
        System.out.println("=== 批量 CSV 转 RDF ===");
        System.out.println("源目录: " + sourceDir);
        System.out.println("目标目录: " + targetDir);
        System.out.println();
        
        Map<String, Object> result = batchConvert(sourceDir, targetDir);
        
        System.out.println();
        System.out.println("=== 转换结果 ===");
        System.out.println("成功: " + result.get("successCount") + " 个文件");
        System.out.println("失败: " + result.get("failedCount") + " 个文件");
    }
}
