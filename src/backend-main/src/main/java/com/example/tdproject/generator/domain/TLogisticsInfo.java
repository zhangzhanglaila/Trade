package com.example.tdproject.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物流信息表（含国家、进出口属性，对应外贸问答模型贸易数据）
 * @TableName t_logistics_info
 */
@TableName(value ="trade_records")
@Data
public class TLogisticsInfo {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数据年月（YYYYMM，如202501）
     */
    @TableField("data_year_month")
    private Integer dataYearMonth;

    /**
     * 贸易伙伴编码
     */
    @TableField("partner_code")
    private String tradePartnerCode;

    /**
     * 贸易伙伴名称（如哈萨克斯坦贸易公司）
     */
    @TableField("partner_name")
    private String tradePartnerName;

    /**
     * 注册地编码
     */
    @TableField("place_code")
    private String registeredPlaceCode;

    /**
     * 注册地名称（如上海市）
     */
    @TableField("place_name")
    private String registeredPlaceName;

    /**
     * 商品编码
     */
    @TableField("commodity_code")
    private String commodityCode;

    /**
     * 商品名称（如99.99%≤含锌量＜99.995%的未锻轧非合金锌）
     */
    @TableField("commodity_name")
    private String commodityName;

    /**
     * 贸易方式编码
     */
    @TableField("method_code")
    private String tradeMethodCode;

    /**
     * 贸易方式名称（如一般贸易）
     */
    @TableField("method_name")
    private String tradeMethodName;

    /**
     * 第一数量（支持小数，如1592297.625）
     */
    @TableField("quantity")
    private BigDecimal firstQuantity;

    /**
     * 第一计量单位（如千克）
     */
    @TableField("unit")
    private String firstUnit;

    /**
     * 人民币金额
     */
    @TableField("amount")
    private BigDecimal rmbAmount;

    /**
     * 国家（兼容接口字段，当前库表无对应列）
     */
    @TableField(exist = false)
    private String country;

    /**
     * 进出口类型（兼容接口字段，当前库表无对应列）
     */
    @TableField(exist = false)
    private Integer importExportType;
}