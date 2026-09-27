-- 兼容当前仍引用 t_news_corpus 的查询场景。
-- 主方案仍应以 Java 代码直接映射 news_articles 为准；该视图用于空环境初始化或兼容只读 SQL。

DROP VIEW IF EXISTS t_news_corpus;

CREATE VIEW t_news_corpus AS
SELECT
    id,
    title AS news_title,
    content AS news_content,
    publish_time,
    NULL AS news_source,
    NULL AS country,
    YEAR(publish_time) AS year
FROM news_articles;
