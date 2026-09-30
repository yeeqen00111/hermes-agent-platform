package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.ToolCall;
import com.hermes.agent.mapper.ToolCallMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具调用审计（契约 §17.1 所有动作可审计）：落库 ai_tool_call，只写只读。
 * 审计是旁路：落库失败只记日志，绝不影响工具主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallAuditService {

    private final ToolCallMapper toolCallMapper;

    /**
     * 记录工具调用
     */
    public void record(ToolCall call) {
        call.setArguments(sanitize(call.getArguments()));
        if (call.getCreateTime() == null) {
            call.setCreateTime(LocalDateTime.now());
        }
        try {
            toolCallMapper.insert(call);
        } catch (Exception e) {
            log.error("工具调用审计落库失败: traceId={}, tool={}", call.getTraceId(), call.getToolCode(), e);
        }
    }

    /**
     * 查询审计记录（只读，无修改/删除入口）
     */
    public List<ToolCall> query(String traceId, String sessionId, String toolCode, String channel, Integer limit) {
        int max = limit == null ? 100 : Math.min(Math.max(limit, 1), 500);
        return toolCallMapper.selectList(new LambdaQueryWrapper<ToolCall>()
                .eq(traceId != null && !traceId.isBlank(), ToolCall::getTraceId, traceId)
                .eq(sessionId != null && !sessionId.isBlank(), ToolCall::getSessionId, sessionId)
                .eq(toolCode != null && !toolCode.isBlank(), ToolCall::getToolCode, toolCode)
                .eq(channel != null && !channel.isBlank(), ToolCall::getChannel, channel)
                .orderByDesc(ToolCall::getId)
                .last("LIMIT " + max));
    }

    /** 参数里可能带连接串，凭据部分一律打码后再落库 */
    private String sanitize(String text) {
        return text == null ? null : text.replaceAll("://[^@\\s]+@", "://***@");
    }
}
