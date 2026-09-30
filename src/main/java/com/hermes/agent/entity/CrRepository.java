package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码仓库
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_repository")
public class CrRepository extends BaseEntity {

    private String repoCode;

    private String name;

    private String repoUrl;

    private String defaultBranch;

    private Long credentialId;

    private String workspaceDir;

    private java.time.LocalDateTime lastSyncTime;

    private String lastRevision;

    /** 仓库级评审提示词（叠加在系统提示词之上） */
    private String reviewPromptExtra;

    private Integer enabled;
}
