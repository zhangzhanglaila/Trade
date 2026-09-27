package com.example.tdproject.ontology.enums;

import lombok.Getter;
import org.apache.jena.riot.Lang;

/**
 * RDF文件格式枚举
 * 支持OWL、RDF/XML、Turtle、N-Triples格式
 */
@Getter
public enum RdfFileFormat {
    
    OWL("OWL", "owl", Lang.RDFXML, "application/rdf+xml", "OWL/XML Ontology"),
    RDF("RDF", "rdf", Lang.RDFXML, "application/rdf+xml", "RDF/XML"),
    TTL("TTL", "ttl", Lang.TURTLE, "text/turtle", "Turtle"),
    NT("NT", "nt", Lang.NTRIPLES, "application/n-triples", "N-Triples"),
    JSON("JSON", "json", null, "application/json", "JSON Format");
    
    private final String code;
    private final String extension;
    private final Lang jenaLang;
    private final String mimeType;
    private final String description;
    
    RdfFileFormat(String code, String extension, Lang jenaLang, String mimeType, String description) {
        this.code = code;
        this.extension = extension;
        this.jenaLang = jenaLang;
        this.mimeType = mimeType;
        this.description = description;
    }
    
    /**
     * 根据code获取枚举
     */
    public static RdfFileFormat fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return OWL;
        }
        String upperCode = code.toUpperCase().trim();
        
        // 直接匹配CODE
        for (RdfFileFormat format : values()) {
            if (format.code.equals(upperCode)) {
                return format;
            }
        }
        
        // 匹配描述名（如 TURTLE, N-TRIPLES）
        for (RdfFileFormat format : values()) {
            if (format.description.toUpperCase().contains(upperCode) ||
                upperCode.contains(format.description.toUpperCase())) {
                return format;
            }
        }
        
        // 特殊匹配
        if (upperCode.contains("TURTLE") || upperCode.equals("TURTLE")) {
            return TTL;
        }
        if (upperCode.contains("TRIPLE") || upperCode.contains("N-TRIPLES")) {
            return NT;
        }
        
        // 尝试根据扩展名匹配
        for (RdfFileFormat format : values()) {
            if (format.extension.equalsIgnoreCase(code)) {
                return format;
            }
        }
        return OWL;
    }
    
    /**
     * 根据文件名扩展名获取格式
     */
    public static RdfFileFormat fromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            return OWL;
        }
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (ext) {
            case "rdf" -> RDF;
            case "ttl" -> TTL;
            case "nt" -> NT;
            default -> OWL;
        };
    }
}