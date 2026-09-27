package com.example.tdproject.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 综合统计数据表
 * @TableName comprehensive_data
 */
@TableName(value ="comprehensive_data")
@Data
public class ComprehensiveData {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 语料条目数
     */
    private Integer corpusEntryCount;

    /**
     * 数据条目数
     */
    private Integer dataEntryCount;

    /**
     * 问答访问数
     */
    private Integer queryVisitCount;

    /**
     * 贸易国家数
     */
    private Integer tradeCountryCount;

    /**
     * 统计年份
     */
    private Integer statYear;

    /**
     * 统计月份
     */
    private Integer statMonth;

    /**
     * 数据更新时间
     */
    private Date updateTime;
}