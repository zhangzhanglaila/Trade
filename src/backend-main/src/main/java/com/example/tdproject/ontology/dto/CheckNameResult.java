package com.example.tdproject.ontology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 本体名称检查结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本体名称检查结果")
public class CheckNameResult {
    
    @Schema(description = "是否存在", example = "true")
    private boolean exists;
    
    @Schema(description = "图数据库中的命名图URI列表")
    private List<String> namedGraphs;
    
    @Schema(description = "当前版本号（如果存在）", example = "1.0")
    private String currentVersion;
}