package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AiChatMessage;
import com.hermes.agent.entity.AiChatSession;
import com.hermes.agent.entity.ToolCall;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiChatMessageMapper;
import com.hermes.agent.mapper.AiChatSessionMapper;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.AlertReportMapper;
import com.hermes.agent.mapper.BizMetricMapper;
import com.hermes.agent.mapper.CrRepositoryMapper;
import com.hermes.agent.mapper.KnowledgeNodeMapper;
import com.hermes.agent.mapper.OpsGrantMapper;
import com.hermes.agent.mapper.SysProjectMapper;
import com.hermes.agent.mapper.SysUserMapper;
import com.hermes.agent.mapper.ToolCallMapper;
import com.hermes.agent.platform.PlatformLogBuffer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台配置（白板·平台层）：平台日志（运行日志缓冲）+ 用量分析（工具调用/会话/消息）+ 数据看板（各模块计数）。
 * 大模型配置在「Agent → 模型」（平台配置页给出指引）。
 */
@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    private static final int AGGREGATE_LIMIT = 5000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PlatformLogBuffer logBuffer;
    private final ToolCallMapper toolCallMapper;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AgentProfileMapper agentProfileMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final AlertReportMapper alertReportMapper;
    private final BizMetricMapper bizMetricMapper;
    private final KnowledgeNodeMapper kbNodeMapper;
    private final OpsGrantMapper opsGrantMapper;
    private final CrRepositoryMapper repoMapper;
    private final SysProjectMapper projectMapper;
    private final SysUserMapper userMapper;

    // ---------- 平台日志 ----------

    public List<Map<String, Object>> logs(String level, String keyword, int limit) {
        return logBuffer.query(level, keyword, limit);
    }

    // ---------- 用量分析 ----------

    public Map<String, Object> usage(int days) {
        int window = Math.max(1, Math.min(days, 90));
        LocalDateTime since = LocalDate.now().minusDays(window - 1L).atStartOfDay();

        List<ToolCall> calls = toolCallMapper.selectList(new LambdaQueryWrapper<ToolCall>()
                .ge(ToolCall::getCreateTime, since)
                .orderByDesc(ToolCall::getId)
                .last("LIMIT " + AGGREGATE_LIMIT));

        int success = 0;
        long durationSum = 0;
        int durationCount = 0;
        Map<String, Integer> byTool = new LinkedHashMap<>();
        Map<String, Integer> byChannel = new LinkedHashMap<>();
        Map<String, Integer> byDay = new LinkedHashMap<>();
        for (ToolCall call : calls) {
            if (Integer.valueOf(1).equals(call.getSuccess())) {
                success++;
            }
            if (call.getDurationMs() != null) {
                durationSum += call.getDurationMs();
                durationCount++;
            }
            bump(byTool, call.getToolCode());
            bump(byChannel, call.getChannel());
            if (call.getCreateTime() != null) {
                bump(byDay, call.getCreateTime().toLocalDate().format(DAY));
            }
        }

        List<AiChatSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                .ge(AiChatSession::getCreateTime, since));
        Map<String, Integer> sessionsByChannel = new LinkedHashMap<>();
        for (AiChatSession s : sessions) {
            bump(sessionsByChannel, s.getChannel());
        }

        List<AiChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .ge(AiChatMessage::getCreateTime, since)
                .last("LIMIT " + AGGREGATE_LIMIT));
        Map<String, Integer> messagesByRole = new LinkedHashMap<>();
        for (AiChatMessage m : messages) {
            bump(messagesByRole, m.getRole());
        }

        Map<String, Object> toolCallBlock = new LinkedHashMap<>();
        toolCallBlock.put("total", calls.size());
        toolCallBlock.put("success", success);
        toolCallBlock.put("failure", calls.size() - success);
        toolCallBlock.put("successRate", calls.isEmpty() ? 0.0
                : Math.round(success * 1000.0 / calls.size()) / 10.0);
        toolCallBlock.put("avgDurationMs", durationCount == 0 ? 0 : durationSum / durationCount);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", window);
        out.put("since", since);
        out.put("toolCalls", toolCallBlock);
        out.put("byTool", toRows("toolCode", byTool));
        out.put("byChannel", toRows("channel", byChannel));
        out.put("byDay", toRows("day", byDay));
        out.put("sessions", Map.of("total", sessions.size(), "byChannel", toRows("channel", sessionsByChannel)));
        out.put("messages", Map.of("total", messages.size(), "byRole", toRows("role", messagesByRole)));
        return out;
    }

    // ---------- 数据看板 ----------

    public Map<String, Object> dashboard() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("agents", agentProfileMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("sessions", sessionMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("messages", messageMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("toolCalls", toolCallMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("alerts", alertRecordMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("alertReports", alertReportMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("bizMetrics", bizMetricMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("kbDocs", kbNodeMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("opsGrants", opsGrantMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("repos", repoMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("projects", projectMapper.selectCount(new LambdaQueryWrapper<>()));
        counts.put("users", userMapper.selectCount(new LambdaQueryWrapper<>()));

        List<ToolCall> recent = toolCallMapper.selectList(new LambdaQueryWrapper<ToolCall>()
                .orderByDesc(ToolCall::getId)
                .last("LIMIT 20"));
        List<Map<String, Object>> recentRows = new ArrayList<>();
        for (ToolCall c : recent) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("traceId", c.getTraceId());
            row.put("toolCode", c.getToolCode());
            row.put("channel", c.getChannel());
            row.put("success", c.getSuccess());
            row.put("durationMs", c.getDurationMs());
            row.put("createTime", c.getCreateTime());
            recentRows.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("logBufferSize", logBuffer.size());
        out.put("recentToolCalls", recentRows);
        return out;
    }

    private static List<Map<String, Object>> toRows(String dimName, Map<String, Integer> counts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((k, v) -> rows.add(new LinkedHashMap<>(Map.of(dimName, k, "count", v))));
        return rows;
    }

    private static void bump(Map<String, Integer> map, String key) {
        map.merge(key == null || key.isBlank() ? "未标注" : key, 1, Integer::sum);
    }
}
