package com.hermes.agent.api;

import com.hermes.agent.entity.ToolCall;
import com.hermes.agent.service.ToolCallAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工具调用审计查询（契约 §17.1 所有动作可审计）：只读，无修改/删除入口
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class ToolCallAuditController {

    private final ToolCallAuditService auditService;

    @GetMapping("/tool-calls")
    public List<ToolCall> list(@RequestParam(required = false) String traceId,
                               @RequestParam(required = false) String sessionId,
                               @RequestParam(required = false) String toolCode,
                               @RequestParam(required = false) String channel,
                               @RequestParam(required = false) Integer limit) {
        return auditService.query(traceId, sessionId, toolCode, channel, limit);
    }
}
