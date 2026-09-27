-- ================================================================
-- 中哈贸易系统 · 主业务库建表脚本
-- 目标库：foreign_trade_qa_db
--
-- 【为什么需要这个脚本】
--   项目原先只提供了 sys_user 的建表 SQL 和两个兼容视图 SQL，
--   其余 7 张业务表在代码里只靠 MyBatis-Plus 实体注解映射，
--   仓库中没有任何 DDL。部署到全新环境时，后端一启动就会因
--   "Table 'xxx' doesn't exist" 报错或功能静默失效。
--   本脚本依据 Java 实体类（@TableName / @TableField）反向补全，
--   字段命名与 mybatis-plus.configuration.map-underscore-to-camel-case
--   的默认规则严格一致（camelCase ⇄ snake_case）。
--
-- 【对应关系（Java 实体 → 表）】
--   generator/domain/TNewsCorpus.java        → news_articles
--   generator/domain/TLogisticsInfo.java     → trade_records
--   generator/domain/CountryMonthlyTrade.java→ country_monthly_trade
--   generator/domain/TradeMethodStats.java   → trade_method_stats
--   generator/domain/ComprehensiveData.java  → comprehensive_data
--   generator/domain/CorpusEntries.java      → corpus_entries
--   generator/domain/Ontology.java           → ontology
--   User/User.java                           → sys_user
--
-- 【用法】
--   mysql -u root -p < schema.sql
--   或由 deploy/init_mysql.py 自动执行
-- ================================================================

CREATE DATABASE IF NOT EXISTS `foreign_trade_qa_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `foreign_trade_qa_db`;

-- ----------------------------------------------------------------
-- 1. sys_user  用户表（登录鉴权，JWT）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`    VARCHAR(64)  NOT NULL                COMMENT '用户名',
  `password`    VARCHAR(255) NOT NULL                COMMENT '密码（BCrypt 加密存储）',
  `nickname`    VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
  `email`       VARCHAR(128) DEFAULT NULL            COMMENT '邮箱',
  `phone`       VARCHAR(32)  DEFAULT NULL            COMMENT '手机号',
  `avatar`      VARCHAR(512) DEFAULT NULL            COMMENT '头像URL',
  `status`      TINYINT      DEFAULT 1               COMMENT '状态：0-禁用 1-启用',
  `create_time` DATETIME     DEFAULT NULL            COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL            COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------------------------------------------
-- 2. news_articles  新闻语料表（问答 RAG 的来源）
--    来源数据：data/04_采集_新闻语料/*.csv（列 content_text → content）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `news_articles` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title`        VARCHAR(512) DEFAULT NULL            COMMENT '新闻标题',
  `content`      LONGTEXT     DEFAULT NULL            COMMENT '新闻内容',
  `publish_time` DATETIME     DEFAULT NULL            COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_title` (`title`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻语料表';

-- ----------------------------------------------------------------
-- 3. trade_records  海关进出口明细表
--    来源数据：data/01_原始_海关进出口明细/{五国}/{出口,进口}/*.csv
--    注意：出口与进口 CSV 的列顺序不同（注册地/商品两组的先后相反），
--          导入时必须按「表头名」映射，不能按下标取值。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_records` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `data_year_month` INT           DEFAULT NULL            COMMENT '数据年月（YYYYMM，如 202501）',
  `partner_code`    VARCHAR(24)   DEFAULT NULL            COMMENT '贸易伙伴编码',
  `partner_name`    VARCHAR(64)   DEFAULT NULL            COMMENT '贸易伙伴名称',
  `place_code`      VARCHAR(24)   DEFAULT NULL            COMMENT '注册地编码',
  `place_name`      VARCHAR(64)   DEFAULT NULL            COMMENT '注册地名称',
  `commodity_code`  VARCHAR(32)   DEFAULT NULL            COMMENT '商品编码',
  `commodity_name`  VARCHAR(512)  DEFAULT NULL            COMMENT '商品名称',
  `method_code`     VARCHAR(24)   DEFAULT NULL            COMMENT '贸易方式编码',
  `method_name`     VARCHAR(64)   DEFAULT NULL            COMMENT '贸易方式名称',
  `quantity`        DECIMAL(24,4) DEFAULT NULL            COMMENT '第一数量',
  `unit`            VARCHAR(32)   DEFAULT NULL            COMMENT '第一计量单位',
  `amount`          DECIMAL(24,4) DEFAULT NULL            COMMENT '人民币金额',
  PRIMARY KEY (`id`),
  KEY `idx_year_month` (`data_year_month`),
  KEY `idx_partner`    (`partner_name`),
  KEY `idx_place`      (`place_name`),
  KEY `idx_method`     (`method_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='海关进出口明细表';

-- ----------------------------------------------------------------
-- 4. country_monthly_trade  国家月度贸易统计（大屏图表数据源）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `country_monthly_trade` (
  `id`              INT           NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `country`         VARCHAR(64)   DEFAULT NULL            COMMENT '国家名称',
  `year`            INT           DEFAULT NULL            COMMENT '年份',
  `month`           INT           DEFAULT NULL            COMMENT '月份',
  `export_quantity` DECIMAL(24,4) DEFAULT NULL            COMMENT '出口量',
  `export_amount`   DECIMAL(24,4) DEFAULT NULL            COMMENT '出口金额（人民币）',
  `import_quantity` DECIMAL(24,4) DEFAULT NULL            COMMENT '进口量',
  `import_amount`   DECIMAL(24,4) DEFAULT NULL            COMMENT '进口金额（人民币）',
  `total_quantity`  DECIMAL(24,4) DEFAULT NULL            COMMENT '进出口总量',
  `total_amount`    DECIMAL(24,4) DEFAULT NULL            COMMENT '进出口总金额',
  PRIMARY KEY (`id`),
  KEY `idx_country_ym` (`country`, `year`, `month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国家月度贸易统计';

-- ----------------------------------------------------------------
-- 5. trade_method_stats  贸易方式统计（大屏饼图数据源）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_method_stats` (
  `id`            INT           NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `country_name`  VARCHAR(64)   DEFAULT NULL            COMMENT '国家名称',
  `trade_method`  VARCHAR(64)   DEFAULT NULL            COMMENT '贸易方式',
  `trade_amount`  DECIMAL(24,4) DEFAULT NULL            COMMENT '贸易金额（人民币）',
  `stat_year`     INT           DEFAULT NULL            COMMENT '统计年份',
  `stat_month`    INT           DEFAULT NULL            COMMENT '统计月份',
  PRIMARY KEY (`id`),
  KEY `idx_country_ym` (`country_name`, `stat_year`, `stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贸易方式统计';

-- ----------------------------------------------------------------
-- 6. comprehensive_data  综合统计（首页数字卡片数据源）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `comprehensive_data` (
  `id`                 INT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `corpus_entry_count` INT      DEFAULT NULL            COMMENT '语料条目数',
  `data_entry_count`   INT      DEFAULT NULL            COMMENT '数据条目数',
  `query_visit_count`  INT      DEFAULT NULL            COMMENT '问答访问数',
  `trade_country_count`INT      DEFAULT NULL            COMMENT '贸易国家数',
  `stat_year`          INT      DEFAULT NULL            COMMENT '统计年份',
  `stat_month`         INT      DEFAULT NULL            COMMENT '统计月份',
  `update_time`        DATETIME DEFAULT NULL            COMMENT '数据更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ym` (`stat_year`, `stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合统计数据';

-- ----------------------------------------------------------------
-- 7. corpus_entries  历年语料条目统计（大屏折线图数据源）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `corpus_entries` (
  `id`          INT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `year`        INT      DEFAULT NULL            COMMENT '年份',
  `entry_count` INT      DEFAULT NULL            COMMENT '语料条目数',
  `update_time` DATETIME DEFAULT NULL            COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_year` (`year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历年语料条目统计';

-- ----------------------------------------------------------------
-- 8. ontology  本体元信息表（本体管理页面）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ontology` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_name`   VARCHAR(255) DEFAULT NULL            COMMENT '项目名称',
  `creator`        VARCHAR(64)  DEFAULT NULL            COMMENT '创建人',
  `version_number` VARCHAR(64)  DEFAULT NULL            COMMENT '版本号',
  `namespace_uri`  VARCHAR(512) DEFAULT NULL            COMMENT '命名空间URI',
  `create_time`    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  `modify_time`    DATETIME     DEFAULT NULL            COMMENT '修改时间',
  `version_status` INT          DEFAULT 1               COMMENT '版本状态 0-历史版本 1-当前使用',
  `parent_id`      BIGINT       DEFAULT NULL            COMMENT '父版本ID',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`version_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本体元信息表';

-- ================================================================
-- 初始数据
-- ================================================================
-- 默认账号（密码为 BCrypt 加密，可用 login 接口验证）
--   admin / admin123
--   test  / test123
-- 若需要改密码，用 deploy/init_mysql.py --reset-password 重新生成。
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `status`, `create_time`, `update_time`)
VALUES
  ('admin', '$2b$10$/WqjUHZIn9zLGLIiu88PfO9BOcUIdpjsxCHMBB0ihC6fVEkhivh6e', '管理员', 'admin@trade.local', 1, NOW(), NOW()),
  ('test',  '$2b$10$3L2vzSRErwSMqvxG.4T3hODx8fCTyudgUIW.d8BKjsvKBwVX76PG6', '测试账号', 'test@trade.local',  1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 本体元信息（对应 deliverables/本体与映射规则/trade（protege建模导出）.rdf）
INSERT INTO `ontology` (`project_name`, `creator`, `version_number`, `namespace_uri`, `create_time`, `modify_time`, `version_status`, `parent_id`)
SELECT '中哈贸易知识图谱本体', 'admin', 'v1.0',
       'http://www.semanticweb.org/lenfrex/ontologies/2025/3/untitled-ontology-10/',
       NOW(), NOW(), 1, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `ontology`);

-- ================================================================
-- 兼容视图（保持与代码中 db/*.sql 的定义一致，可重复执行）
-- ================================================================
DROP VIEW IF EXISTS `t_news_corpus`;
CREATE VIEW `t_news_corpus` AS
SELECT `id`, `title` AS news_title, `content` AS news_content, `publish_time`,
       NULL AS news_source, NULL AS country, YEAR(`publish_time`) AS year
FROM `news_articles`;

DROP VIEW IF EXISTS `t_logistics_info`;
CREATE VIEW `t_logistics_info` AS
SELECT `id`, CAST(`data_year_month` AS UNSIGNED) AS data_year_month,
       `partner_code` AS trade_partner_code, `partner_name` AS trade_partner_name,
       `place_code` AS registered_place_code, `place_name` AS registered_place_name,
       `commodity_code`, `commodity_name`,
       `method_code` AS trade_method_code, `method_name` AS trade_method_name,
       `quantity` AS first_quantity, `unit` AS first_unit, `amount` AS rmb_amount,
       NULL AS country, NULL AS import_export_type
FROM `trade_records`;

-- ================================================================
-- 统计核对（执行后可用下面语句确认数据规模）
-- ================================================================
-- SELECT 'news_articles' AS t, COUNT(*) FROM news_articles
-- UNION ALL SELECT 'trade_records', COUNT(*) FROM trade_records
-- UNION ALL SELECT 'country_monthly_trade', COUNT(*) FROM country_monthly_trade
-- UNION ALL SELECT 'trade_method_stats', COUNT(*) FROM trade_method_stats
-- UNION ALL SELECT 'corpus_entries', COUNT(*) FROM corpus_entries
-- UNION ALL SELECT 'comprehensive_data', COUNT(*) FROM comprehensive_data;
