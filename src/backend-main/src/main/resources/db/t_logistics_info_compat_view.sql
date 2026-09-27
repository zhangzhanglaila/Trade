-- 兼容当前仍引用 t_logistics_info 的查询场景。
-- 主方案仍应以 Java 代码直接映射 trade_records 为准；该视图用于空环境初始化或兼容只读 SQL。

DROP VIEW IF EXISTS t_logistics_info;

CREATE VIEW t_logistics_info AS
SELECT
    id,
    CAST(data_year_month AS UNSIGNED) AS data_year_month,
    partner_code AS trade_partner_code,
    partner_name AS trade_partner_name,
    place_code AS registered_place_code,
    place_name AS registered_place_name,
    commodity_code,
    commodity_name,
    method_code AS trade_method_code,
    method_name AS trade_method_name,
    quantity AS first_quantity,
    unit AS first_unit,
    amount AS rmb_amount,
    NULL AS country,
    NULL AS import_export_type
FROM trade_records;
