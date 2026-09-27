package com.example.tdproject.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 贸易方式统计数据表
 * @TableName trade_method_stats
 */
@TableName(value ="trade_method_stats")
@Data
public class TradeMethodStats {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 国家名称
     */
    private String countryName;

    /**
     * 贸易方式（如一般贸易、加工贸易等）
     */
    private String tradeMethod;

    /**
     * 贸易金额（人民币）
     */
    private BigDecimal tradeAmount;

    /**
     * 统计年份
     */
    private Integer statYear;

    /**
     * 统计月份
     */
    private Integer statMonth;
}