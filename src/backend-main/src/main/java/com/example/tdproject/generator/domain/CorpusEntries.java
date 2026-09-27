package com.example.tdproject.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 历年语料条目统计表
 * @TableName corpus_entries
 */
@TableName(value ="corpus_entries")
@Data
public class CorpusEntries {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 年份
     */
    private Integer year;

    /**
     * 语料条目数
     */
    private Integer entryCount;

    /**
     * 更新时间
     */
    private Date updateTime;
}