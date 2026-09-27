package com.example.tdproject.utils;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CSV 转 RDF 转换器（硬编码版本）
 * 专门针对中亚贸易数据结构
 */
public class CsvToRdfConverter {
    
    // 命名空间
    private static final String NS = "http://example.org/trade#";
    private static final String RES = "http://example.org/resource/";
    
    /**
     * 转换 CSV 为 RDF
     * 
     * @param csvInput CSV 文件输入流
     * @param countryCode 国家代码（如 KZ, UZ）
     * @param dataPeriod 数据时间段（如 2015-01）
     * @return RDF 字节数组
     */
    public static byte[] convert(InputStream csvInput, String countryCode, String dataPeriod) throws IOException {
        // 读取 CSV 内容（自动检测编码）
        byte[] bytes = csvInput.readAllBytes();
        
        // 尝试 UTF-8 解码，如果失败则尝试 GBK
        String content;
        try {
            // 先尝试 UTF-8
            content = new String(bytes, StandardCharsets.UTF_8);
            // 验证：如果包含乱码特征，改用 GBK
            if (content.contains("�") || content.contains("ï¿½")) {
                content = new String(bytes, "GBK");
                System.out.println("[CsvToRdf] 使用 GBK 编码解码");
            } else {
                System.out.println("[CsvToRdf] 使用 UTF-8 编码解码");
            }
        } catch (Exception e) {
            // 如果 UTF-8 失败，使用 GBK
            content = new String(bytes, "GBK");
            System.out.println("[CsvToRdf] 解码失败，回退到 GBK");
        }
        
        String[] lines = content.split("\r?\n");
        
        if (lines.length < 2) {
            throw new RuntimeException("CSV 文件为空或格式错误");
        }
        
        // 创建 RDF 模型
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("trade", NS);
        model.setNsPrefix("res", RES);
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("owl", OWL.getURI());
        
        // ========== 关键修复：声明类为 owl:Class ==========
        Resource goodsClass = model.createResource(NS + "货物")
            .addProperty(RDF.type, OWL.Class)
            .addProperty(RDFS.label, "货物");
        
        Resource countryClass = model.createResource(NS + "国家")
            .addProperty(RDF.type, OWL.Class)
            .addProperty(RDFS.label, "国家");
        
        Resource provinceClass = model.createResource(NS + "省份")
            .addProperty(RDF.type, OWL.Class)
            .addProperty(RDFS.label, "省份");
        
        Resource tradeRecordClass = model.createResource(NS + "交易记录")
            .addProperty(RDF.type, OWL.Class)
            .addProperty(RDFS.label, "交易记录");
        
        // ========== 关键修复：声明属性类型 ==========
        Property involvesGoodsProp = model.createProperty(NS, "涉及货物");
        involvesGoodsProp.addProperty(RDF.type, OWL.ObjectProperty);
        
        Property tradePartnerProp = model.createProperty(NS, "贸易伙伴");
        tradePartnerProp.addProperty(RDF.type, OWL.ObjectProperty);
        
        Property registerProvinceProp = model.createProperty(NS, "注册省份");
        registerProvinceProp.addProperty(RDF.type, OWL.ObjectProperty);
        
        Property goodsCodeProp = model.createProperty(NS, "商品编码");
        goodsCodeProp.addProperty(RDF.type, OWL.DatatypeProperty);
        
        Property countryCodeProp = model.createProperty(NS, "国家代码");
        countryCodeProp.addProperty(RDF.type, OWL.DatatypeProperty);
        
        Property provinceCodeProp = model.createProperty(NS, "省份代码");
        provinceCodeProp.addProperty(RDF.type, OWL.DatatypeProperty);
        
        // 解析表头
        String[] headers = parseLine(lines[0]);
        System.out.println("[CsvToRdf] 表头: " + Arrays.toString(headers));
        
        // 创建实体容器（用于去重）
        Set<String> goodsSet = new HashSet<>();
        Set<String> countrySet = new HashSet<>();
        Set<String> provinceSet = new HashSet<>();
        
        int rowNum = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            String[] values = parseLine(line);
            if (values.length < 14) {
                System.out.println("[CsvToRdf] 跳过行 " + i + ", 列数不足: " + values.length);
                continue;
            }
            
            rowNum++;
            
            // 提取字段（按列顺序）
            String dataYearMonth = values[0];      // 数据年月
            String partnerCode = values[1];        // 贸易伙伴编码
            String partnerName = values[2];        // 贸易伙伴名称
            String regCode = values[3];            // 注册地编码
            String regName = values[4];            // 注册地名称
            String goodsCode = values[5];          // 商品编码
            String goodsName = values[6];          // 商品名称
            String tradeModeCode = values[7];      // 贸易方式编码
            String tradeModeName = values[8];      // 贸易方式名称
            String qty1 = values[9];               // 第一数量
            String unit1 = values[10];             // 第一计量单位
            String qty2 = values[11];              // 第二数量
            String unit2 = values[12];             // 第二计量单位
            String amount = values[13];            // 人民币
            
            // 清理数据
            goodsCode = goodsCode.trim();
            partnerCode = partnerCode.trim();
            regCode = regCode.trim();
            
            // 生成 URI
            String goodsUri = RES + "goods/" + goodsCode;
            String partnerUri = RES + "country/" + partnerCode;
            String provinceUri = RES + "province/" + regCode;
            String tradeUri = RES + "trade/" + dataPeriod + "/" + countryCode + "/" + rowNum;
            
            // 1. 创建货物实体（去重）
            if (!goodsSet.contains(goodsCode)) {
                goodsSet.add(goodsCode);
                Resource goods = model.createResource(goodsUri);
                goods.addProperty(RDF.type, goodsClass);  // 使用 goodsClass
                goods.addProperty(RDF.type, OWL2.NamedIndividual);  // 同时声明为NamedIndividual
                goods.addProperty(goodsCodeProp, goodsCode);
                goods.addProperty(RDFS.label, goodsName);
            }
            
            // 2. 创建国家实体（去重）
            if (!countrySet.contains(partnerCode)) {
                countrySet.add(partnerCode);
                Resource country = model.createResource(partnerUri);
                country.addProperty(RDF.type, countryClass);  // 使用 countryClass
                country.addProperty(RDF.type, OWL2.NamedIndividual);  // 同时声明为NamedIndividual
                country.addProperty(countryCodeProp, partnerCode);
                country.addProperty(RDFS.label, partnerName);
            }
            
            // 3. 创建省份实体（去重）
            if (!provinceSet.contains(regCode)) {
                provinceSet.add(regCode);
                Resource province = model.createResource(provinceUri);
                province.addProperty(RDF.type, provinceClass);  // 使用 provinceClass
                province.addProperty(RDF.type, OWL2.NamedIndividual);  // 同时声明为NamedIndividual
                province.addProperty(provinceCodeProp, regCode);
                province.addProperty(RDFS.label, regName);
            }
            
            // 4. 创建交易记录
            Resource trade = model.createResource(tradeUri);
            trade.addProperty(RDF.type, tradeRecordClass);  // 使用 tradeRecordClass
            trade.addProperty(RDF.type, OWL2.NamedIndividual);  // 同时声明为NamedIndividual
            trade.addProperty(model.createProperty(NS, "数据年月"), dataYearMonth);
            trade.addProperty(involvesGoodsProp, model.createResource(goodsUri));
            trade.addProperty(tradePartnerProp, model.createResource(partnerUri));
            trade.addProperty(registerProvinceProp, model.createResource(provinceUri));
            trade.addProperty(model.createProperty(NS, "贸易方式代码"), tradeModeCode);
            trade.addProperty(model.createProperty(NS, "贸易方式"), tradeModeName);
            
            // 添加数量和金额
            if (!qty1.isEmpty()) {
                trade.addProperty(model.createProperty(NS, "第一数量"), qty1);
            }
            if (!unit1.isEmpty()) {
                trade.addProperty(model.createProperty(NS, "第一计量单位"), unit1);
            }
            if (!qty2.isEmpty() && !qty2.equals("0")) {
                trade.addProperty(model.createProperty(NS, "第二数量"), qty2);
            }
            if (!unit2.isEmpty() && !unit2.equals("?")) {
                trade.addProperty(model.createProperty(NS, "第二计量单位"), unit2);
            }
            if (!amount.isEmpty()) {
                // 清理金额中的逗号
                String cleanAmount = amount.replace(",", "").replace("\"", "").trim();
                trade.addProperty(model.createProperty(NS, "金额"), cleanAmount);
            }
        }
        
        System.out.println("[CsvToRdf] 转换完成: " + rowNum + " 条记录");
        System.out.println("[CsvToRdf] 货物: " + goodsSet.size() + ", 国家: " + countrySet.size() + ", 省份: " + provinceSet.size());
        
        // 输出为 RDF/XML
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        model.write(out, "RDF/XML");
        
        // ========== 调试打印：验证生成的 RDF 结构 ==========
        byte[] rdfData = out.toByteArray();
        System.out.println("\n========== 生成的 RDF (调试用) ==========");
        System.out.println(new String(rdfData, StandardCharsets.UTF_8));
        System.out.println("========================================");
        System.out.println("三元组总数: " + model.size());
        System.out.println("类数量 (owl:Class): " + model.listResourcesWithProperty(RDF.type, OWL.Class).toList().size());
        System.out.println("对象属性数量: " + model.listResourcesWithProperty(RDF.type, OWL.ObjectProperty).toList().size());
        System.out.println("数据属性数量: " + model.listResourcesWithProperty(RDF.type, OWL.DatatypeProperty).toList().size());
        System.out.println("实例数量 (有rdf:type的): " + model.listSubjectsWithProperty(RDF.type).toList().size());
        System.out.println("========================================\n");
        
        return rdfData;
    }
    
    /**
     * 解析 CSV 行（处理引号内的逗号）
     */
    private static String[] parseLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        
        return result.toArray(new String[0]);
    }
}
