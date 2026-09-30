package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 配置不可变快照（发布时写死，永不修改；回滚只移指针）
 */
@Data
@TableName("ai_agent_version")
public class AgentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentCode;

    private Integer version;

    /** profile + 身份包上下文文件的 JSON 快照 */
    private String snapshot;

    private LocalDateTime publishTime;

    private String publishBy;

    private LocalDateTime createTime;
}
