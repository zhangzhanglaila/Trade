package com.example.tdproject.utils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RDF URI 修复工具
 * 修复包含中文和全角符号的非法 URI
 */
public class RdfUriFixer {
    
    // 匹配 URI 属性的正则：rdf:about="..." 或 rdf:resource="..."
    private static final Pattern URI_PATTERN = Pattern.compile(
        "(rdf:(?:about|resource)=\")([^\"]+)(\")"
    );
    
    /**
     * 修复 RDF 文件中的非法 URI
     */
    public static byte[] fixRdfUri(byte[] rdfContent) {
        String content = new String(rdfContent, StandardCharsets.UTF_8);
        
        System.out.println("[RdfUriFixer] 开始修复，原始长度: " + content.length());
        
        // 使用正则替换所有匹配的属性
        Matcher matcher = URI_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        int count = 0;
        
        while (matcher.find()) {
            String prefix = matcher.group(1);  // rdf:about=" 或 rdf:resource="
            String uri = matcher.group(2);     // URI 值
            String suffix = matcher.group(3);  // "
            
            // 编码 URI
            String encodedUri = encodeUri(uri);
            
            // 如果 URI 被修改了，记录日志
            if (!uri.equals(encodedUri)) {
                count++;
                if (count <= 3) {  // 只显示前3个
                    System.out.println("[RdfUriFixer] 修复 URI: " + uri.substring(0, Math.min(50, uri.length())) + "...");
                }
            }
            
            // 替换
            matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + encodedUri + suffix));
        }
        matcher.appendTail(sb);
        
        String result = sb.toString();
        System.out.println("[RdfUriFixer] 修复完成，共修复 " + count + " 个 URI");
        
        return result.getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * 对 URI 进行编码
     */
    private static String encodeUri(String uri) {
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < uri.length(); i++) {
            char c = uri.charAt(i);
            
            // 允许的字符（RFC 3986）
            if (isUnreserved(c) || isReserved(c)) {
                result.append(c);
            } else if (c == '%' && isValidPercentEncoding(uri, i)) {
                // 已经是合法的 %XX 编码，保留
                result.append(c);
            } else {
                // 需要编码的字符（中文、全角符号等）
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    result.append(String.format("%%%02X", b & 0xFF));
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * 检查是否是合法的 %XX 编码
     */
    private static boolean isValidPercentEncoding(String s, int percentIndex) {
        if (percentIndex + 2 >= s.length()) return false;
        char c1 = s.charAt(percentIndex + 1);
        char c2 = s.charAt(percentIndex + 2);
        return isHexDigit(c1) && isHexDigit(c2);
    }
    
    /**
     * 未保留字符（不需要编码）
     */
    private static boolean isUnreserved(char c) {
        return (c >= 'a' && c <= 'z') ||
               (c >= 'A' && c <= 'Z') ||
               (c >= '0' && c <= '9') ||
               c == '-' || c == '_' || c == '.' || c == '~';
    }
    
    /**
     * 保留字符（不需要编码）
     */
    private static boolean isReserved(char c) {
        return c == ':' || c == '/' || c == '?' || c == '#' ||
               c == '[' || c == ']' || c == '@' ||
               c == '!' || c == '$' || c == '&' || c == '(' ||
               c == ')' || c == '*' || c == '+' || c == ',' ||
               c == ';' || c == '=';
    }
    
    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || 
               (c >= 'a' && c <= 'f') || 
               (c >= 'A' && c <= 'F');
    }
}
