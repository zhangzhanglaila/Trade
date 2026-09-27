package com.example.tdproject.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 新闻语料表（含国家、年份属性，对应外贸问答模型新闻数据）
 * @TableName t_news_corpus
 */
@TableName(value ="news_articles")
@Data
public class TNewsCorpus {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 新闻标题（如哈萨克斯坦2024年1-5月外贸数据）
     */
    @TableField("title")
    private String newsTitle;

    /**
     * 新闻内容（含外贸额、进出口商品等详情）
     */
    @TableField("content")
    private String newsContent;

    /**
     * 新闻发布时间（如2024-08-05 00:00:00）
     */
    @TableField("publish_time")
    private Date publishTime;

    /**
     * 新闻来源（当前库表无对应列）
     */
    @TableField(exist = false)
    private String newsSource;

    /**
     * 国家（兼容接口字段，当前通过标题模糊匹配）
     */
    @TableField(exist = false)
    private String country;

    /**
     * 年份（兼容接口字段，当前通过 publish_time 提取年份）
     */
    @TableField(exist = false)
    private Integer year;
}