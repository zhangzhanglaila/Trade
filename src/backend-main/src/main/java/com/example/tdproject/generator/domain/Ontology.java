package com.example.tdproject.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 本体表
 * @TableName ontology
 */
@TableName(value ="ontology")
@Data
public class Ontology {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 版本号
     */
    private String versionNumber;

    /**
     * 命名空间URI
     */
    private String namespaceUri;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改时间
     */
    private Date modifyTime;
    // 新增版本状态 0-历史版本 1-当前使用
    private Integer versionStatus;

    // 新增父版本ID
    private Long parentId;
}