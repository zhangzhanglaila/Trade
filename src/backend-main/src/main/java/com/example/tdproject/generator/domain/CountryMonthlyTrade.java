package com.example.tdproject.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 国家月度贸易数据表
 * @TableName country_monthly_trade
 */
@TableName(value ="country_monthly_trade")
@Data
public class CountryMonthlyTrade {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 国家名称
     */
    private String country;

    /**
     * 年份
     */
    private Integer year;

    /**
     * 月份
     */
    private Integer month;

    /**
     * 出口量
     */
    private BigDecimal exportQuantity;

    /**
     * 出口金额（人民币）
     */
    private BigDecimal exportAmount;

    /**
     * 进口量
     */
    private BigDecimal importQuantity;

    /**
     * 进口金额（人民币）
     */
    private BigDecimal importAmount;

    /**
     * 进出口总量
     */
    private BigDecimal totalQuantity;

    /**
     * 进出口总金额
     */
    private BigDecimal totalAmount;
}