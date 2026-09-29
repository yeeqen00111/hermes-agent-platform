package com.hermes.agent.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具调用审计服务（内存版，后续持久化到数据库）
 */
@Slf4j
@Service
public class ToolCallAuditService {

    private final List<ToolCallRecord> records = new CopyOnWriteArrayList<>();

    /**
     * 记录工具调用
     */
    public void record(ToolCallRecord record) {
        record.setCreateTime(LocalDateTime.now());
        records.add(record);
        log.info("审计记录: traceId={}, tool={}, success={}",
                record.getTraceId(), record.getToolCode(), record.getSuccess());
    }

    /**
     * 查询审计记录
     */
    public List<ToolCallRecord> query(String traceId, String sessionId, String toolCode) {
        return records.stream()
                .filter(r -> traceId == null || traceId.equals(r.getTraceId()))
                .filter(r -> sessionId == null || sessionId.equals(r.getSessionId()))
                .filter(r -> toolCode == null || toolCode.equals(r.getToolCode()))
                .toList();
    }

    @Data
    public static class ToolCallRecord {
        private Long id;
        private String traceId;
        private String sessionId;
        private String agentCode;
        private String toolCode;
        private String safetyLevel;
        private String arguments;
        private Boolean success;
        private String errorCode;
        private Long durationMs;
        private String approvalChoice;
        private Long approvalBy;
        private LocalDateTime approvalTime;
        private String channel;
        private LocalDateTime createTime;
    }
}
