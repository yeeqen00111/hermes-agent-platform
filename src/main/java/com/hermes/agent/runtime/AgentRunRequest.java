package com.hermes.agent.runtime;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AgentRunRequest {

    private String agentCode;

    private Long userId;

    private String sessionId;

    private String userInput;

    private String traceId;

    /** 调用渠道：chat / flow / review，审计落库用 */
    private String channel;

    /** 业务键（如评审任务UUID），用于固定流程运行记录关联 */
    private String bizKey;

    /** 任务级附加系统上下文（如评审任务的仓库/分支/提交区间） */
    private String extraContext;

    /** 数据范围（服务端由 X-Data-Scope 注入，下游工具调用据此校验） */
    private Map<String, List<String>> dataScope;

    /** 目标环境（X-Actor-Env），工具未显式传 environment 时的默认值 */
    private String environment;
}
