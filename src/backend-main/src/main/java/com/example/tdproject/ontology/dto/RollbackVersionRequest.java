package com.example.tdproject.ontology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 版本回滚请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "版本回滚请求")
public class RollbackVersionRequest {
    
    @NotNull(message = "本体ID不能为空")
    @Schema(description = "源本体ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceOntologyId;
    
    @NotBlank(message = "目标版本号不能为空")
    @Schema(description = "目标版本号", example = "1.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newVersion;
}