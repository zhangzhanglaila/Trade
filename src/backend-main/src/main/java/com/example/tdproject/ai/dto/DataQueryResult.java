package com.example.tdproject.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 历史数据查询结果（DATA_QUERY 路由）。
 *
 * <p>与 {@link PredictResponse} 的区别：预测面向「未来」，本类面向「已经发生的真实数据」。
 * 两者共用 {@code trade_in/trade_out} 的四键口径，但取值方式完全不同 —— 这里是查库，
 * 不做任何模型推理。</p>
 */
@Data
@Builder
public class DataQueryResult {

    /** in / out */
    private String tradeType;
    /** price / quantity / null(两者都返回) */
    private String target;

    private String partnerName;
    /** 实际用于查询的商品名（可能是把用户的简称解析后的规范名） */
    private String productName;
    /** 用户原话里的商品名，与 productName 不同时说明做过解析 */
    private String productInput;
    private String tradeMode;
    private String registerName;

    /** 库中该表的实际数据年月范围，如 201501~202503 */
    private String dataRange;

    /** 命中的月份数 */
    private Integer hitMonths;
    /** 明细：按月倒序，每项含 ym/quantity/rmb/price/unit */
    private List<DataRow> rows;

    /** 人类可读的结论（控制器直接当 answer 用） */
    private String summary;

    /** 查不到时的相近建议（商品名 / 贸易方式 / 注册地 / 贸易伙伴） */
    private List<String> suggestions;

    /** 是否因商品名无法解析而提前返回 */
    private Boolean productUnresolved;

    /**
     * 本次结果是否混合了多种计量单位。
     *
     * <p>不指定商品名时，不同商品的计量单位不同（千克/米/台…），把它们的「数量」相加
     * 是没有意义的（会得到「5 亿米」这种假数字）。此时 quantity/price 一律置空，
     * 只保留金额（人民币，单位一致可加），并在 summary 里说明原因。</p>
     */
    private Boolean mixedUnit;
    /** 涉及的计量单位样例 */
    private List<String> unitSamples;

    @Data
    @Builder
    public static class DataRow {
        /** 形如 202503 的整数年月 */
        private Integer ym;
        /** 形如 2025-03 */
        private String label;
        private Double quantity;
        private Double rmb;
        private Double price;
        private String unit;
    }
}
