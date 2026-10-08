package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日志 JSON 解析规则（白板·系统管理）：把采集到的 JSON 日志映射成规范字段。
 * 五个解析级别：系统级别（系统字段映射）/ 时间级别（时间字段+格式）/
 * 日志级别（级别字段+级别映射）/ 内容级别（内容字段）/ 服务级别（服务字段映射）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_log_parse_rule")
public class LogParseRule extends BaseEntity {

    private String code;

    private String name;

    /** 关联采集通道编码 */
    private String channelCode;

    /** 系统级别解析：系统字段映射（JSON key，如 app） */
    private String systemField;

    /** 时间级别解析：时间字段（如 @timestamp） */
    private String timeField;

    /** 时间级别解析：时间格式（如 yyyy-MM-dd HH:mm:ss.SSS） */
    private String timeFormat;

    /** 日志级别解析：级别字段（如 level） */
    private String levelField;

    /** 日志级别映射 JSON：{"WARN":"WARNING"} */
    private String levelMapping;

    /** 内容级别解析：内容字段（如 message） */
    private String contentField;

    /** 服务级别解析：服务字段映射（如 service） */
    private String serviceField;

    /** 示例日志 JSON（用于解析预览） */
    private String sampleJson;

    private Integer enabled;

    private String remark;
}
