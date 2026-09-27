package com.example.tdproject.ontology.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 版本信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "版本信息")
public class VersionInfo {
    
    @Schema(description = "版本ID", example = "1")
    private Long id;
    
    @Schema(description = "版本号", example = "1.0")
    private String version;
    
    @Schema(description = "创建人", example = "张三")
    private String creator;
    
    @Schema(description = "版本状态：1-当前版本，0-历史版本")
    private Integer status;
    
    @Schema(description = "命名空间URI")
    private String namespaceUri;
    
    @Schema(description = "父版本ID")
    private Long parentId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    @Schema(description = "创建时间")
    private Date createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    @Schema(description = "更新时间")
    private Date updateTime;
}