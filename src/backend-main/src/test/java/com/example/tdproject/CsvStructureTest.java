package com.example.tdproject;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;

/**
 * 查看 CSV 文件结构
 */
public class CsvStructureTest {
    
    /** 项目工作区根目录；可用 -Dtrade.workspace=... 覆盖 */
    private static final String WORKSPACE =
            System.getProperty("trade.workspace", "../..");
    
    @Test
    public void testReadCsvStructure() throws Exception {
        // 查找 CSV 文件
        Path baseDir = Paths.get(WORKSPACE + "/data/01_原始_海关进出口明细");
        
        Files.walk(baseDir)
            .filter(p -> p.toString().endsWith(".csv"))
            .findFirst()
            .ifPresent(csvFile -> {
                try {
                    System.out.println("=== CSV 文件结构分析 ===");
                    System.out.println("文件: " + csvFile);
                    System.out.println();
                    
                    // 读取前 5 行
                    BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8);
                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null && count < 5) {
                        System.out.println("行 " + count + ": " + line);
                        count++;
                    }
                    reader.close();
                    
                    System.out.println();
                    System.out.println("=== 列分析 ===");
                    // 重新读取获取表头
                    reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8);
                    String header = reader.readLine();
                    if (header != null) {
                        String[] columns = header.split(",");
                        for (int i = 0; i < columns.length; i++) {
                            System.out.println("列 " + i + ": " + columns[i].replace("\"", ""));
                        }
                    }
                    reader.close();
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
    }
}
