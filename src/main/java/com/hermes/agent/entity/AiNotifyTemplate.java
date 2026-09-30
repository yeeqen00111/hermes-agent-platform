package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警/通知模板（◆ 复用供应链控制塔，平台侧为集成位+本地回退），{{var}} 占位符渲染
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_notify_template")
public class AiNotifyTemplate extends BaseEntity {

    private String code;

    private String name;

    private String channelType;

    private String titleTemplate;

    private String contentTemplate;

    private Integer enabled;
}
