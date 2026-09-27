package com.example.tdproject.ontology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本体入库请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本体入库请求")
public class ImportToHouseRequest {
    
    @NotNull(message = "本体ID不能为空")
    @Schema(description = "源本体ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceOntologyId;
    
    @NotBlank(message = "新版本号不能为空")
    @Schema(description = "新版本号", example = "1.1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newVersion;
    
    @Schema(description = "版本说明", example = "更新类定义")
    private String remark;
}