package com.hermes.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.approval.ApprovalService;
import com.hermes.agent.approval.ApprovalSink;
import com.hermes.agent.common.enums.ApprovalChoice;
import com.hermes.agent.common.enums.SafetyLevel;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.entity.ApprovalRequest;
import com.hermes.agent.entity.ToolCall;
import com.hermes.agent.service.ToolCallAuditService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 工具执行器。
 * 每次调用无论成功/失败/被拒/被禁都会落一条 ai_tool_call 审计（契约 §17.1）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final Guardrail guardrail;
    private final ApprovalService approvalService;
    private final ToolCallAuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * 无审批交互能力的调用（固定流程、定时任务）：WRITE/CONTROLLED 工具一律拒绝
     */
    public ToolResponse execute(String toolCode, ToolRequest request) {
        return execute(toolCode, request, null);
    }

    /**
     * 执行工具调用
     *
     * @param approvalSink 审批交互出口，null 表示调用方无法与人对话
     */
    public ToolResponse execute(String toolCode, ToolRequest request, ApprovalSink approvalSink) {
        long startTime = System.currentTimeMillis();
        ApprovalInfo approvalInfo = new ApprovalInfo();
        ToolResponse response;
        try {
            response = executeInternal(toolCode, request, approvalSink, startTime, approvalInfo);
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolCode, e);
            response = errorResponse(toolCode, "EXECUTION_ERROR",
                    "工具执行失败: " + e.getMessage(), startTime);
        }
        recordAudit(toolCode, request, response, approvalInfo, startTime);
        return response;
    }

    private ToolResponse executeInternal(String toolCode, ToolRequest request, ApprovalSink approvalSink,
                                         long startTime, ApprovalInfo approvalInfo) {
        // 1. 查找工具定义
        Optional<ToolDefinition> toolOpt = toolRegistry.getTool(toolCode);
        if (toolOpt.isEmpty()) {
            return errorResponse(toolCode, "TOOL_NOT_FOUND", "工具不存在: " + toolCode, startTime);
        }

        ToolDefinition toolDef = toolOpt.get();

        // 2. Guardrail校验
        var validationResult = guardrail.validate(toolDef, request);
        if (!validationResult.valid()) {
            return errorResponse(toolCode, validationResult.errorCode(),
                    validationResult.errorMessage(), startTime);
        }

        // 3. 禁止级工具根本不暴露；WRITE/CONTROLLED 必须过审批门
        if (toolDef.getSafetyLevel() == SafetyLevel.FORBIDDEN) {
            return errorResponse(toolCode, "TOOL_FORBIDDEN", "该操作被禁止: " + toolCode, startTime);
        }
        if (requiresApproval(toolDef)) {
            ToolResponse rejected = passApprovalGate(toolDef, request, approvalSink, startTime, approvalInfo);
            if (rejected != null) {
                return rejected;
            }
        }

        // 4. 执行工具（当前用Mock）
        return mockExecute(toolCode, request, startTime);
    }

    private boolean requiresApproval(ToolDefinition toolDef) {
        SafetyLevel level = toolDef.getSafetyLevel();
        return level == SafetyLevel.WRITE || level == SafetyLevel.CONTROLLED;
    }

    /**
     * 审批门：白名单/会话放行直接过；否则建单→推卡片→阻塞等应答。
     * 无论放行还是拒绝，审批决策都会写入 approvalInfo 供审计落库。
     *
     * @return null 表示放行，非 null 为拒绝响应
     */
    private ToolResponse passApprovalGate(ToolDefinition toolDef, ToolRequest request,
                                          ApprovalSink sink, long startTime, ApprovalInfo approvalInfo) {
        String toolCode = toolDef.getToolCode();
        if (approvalService.isPreApproved(toolCode, request.getSessionId(), request.getUserId())) {
            log.info("工具 {} 命中免审（白名单或会话级放行）", toolCode);
            approvalInfo.setChoice(approvalService.isSessionGranted(request.getSessionId(), toolCode)
                    ? "session_grant" : "whitelist");
            return null;
        }
        if (sink == null) {
            log.warn("工具 {} 需人工审批，但调用方无审批交互能力，拒绝执行", toolCode);
            approvalInfo.setChoice("required");
            return errorResponse(toolCode, "APPROVAL_REQUIRED",
                    "该操作需要人工审批，当前调用渠道不支持审批交互", startTime);
        }

        ApprovalRequest card = approvalService.create(toolDef, request);
        sink.onApprovalRequest(card);
        ApprovalChoice choice = approvalService.await(card.getRequestId());
        // 审批单已终结（行已落库），读回决策人/决策时间供审计
        ApprovalRequest decided = approvalService.findByRequestId(card.getRequestId());
        if (decided != null) {
            approvalInfo.setChoice(decided.getChoice());
            approvalInfo.setDecidedBy(decided.getDecidedBy());
            approvalInfo.setDecideTime(decided.getDecideTime());
        }
        if (choice.isAllowed()) {
            return null;
        }
        if (choice == ApprovalChoice.TIMEOUT || choice == ApprovalChoice.CANCELLED) {
            sink.onApprovalCancel(card.getRequestId(), choice.name().toLowerCase());
        }
        String errorCode = switch (choice) {
            case TIMEOUT -> "APPROVAL_TIMEOUT";
            case CANCELLED -> "APPROVAL_CANCELLED";
            default -> "APPROVAL_DENIED";
        };
        return errorResponse(toolCode, errorCode, rejectMessage(choice), startTime);
    }

    private String rejectMessage(ApprovalChoice choice) {
        return switch (choice) {
            case TIMEOUT -> "审批超时未应答，操作已被拒绝";
            case CANCELLED -> "审批已撤回，操作已被拒绝";
            default -> "操作被审批人拒绝";
        };
    }

    /**
     * Mock执行（开发阶段）
     */
    private ToolResponse mockExecute(String toolCode, ToolRequest request, long startTime) {
        log.info("Mock执行工具: {}, 参数: {}", toolCode, request.getArguments());

        ToolResponse response = new ToolResponse();
        response.setSuccess(true);
        response.setData(createMockData(toolCode));
        response.setCitations(createMockCitations(toolCode));
        response.setDurationMs(System.currentTimeMillis() - startTime);

        return response;
    }

    private Object createMockData(String toolCode) {
        // 返回简单的Mock数据
        return switch (toolCode) {
            case "log.search" -> new Object[]{
                    Map.of("eventId", "mock-001", "message", "Mock log entry", "level", "ERROR")
            };
            case "alert.query" -> new Object[]{
                    Map.of("alertId", 1, "severity", "P1", "status", "NEW")
            };
            default -> Map.of("message", "Mock data for " + toolCode);
        };
    }

    private java.util.List<com.hermes.agent.dto.ToolResponse.Citation> createMockCitations(String toolCode) {
        var citation = new com.hermes.agent.dto.ToolResponse.Citation();
        citation.setKind("log");
        citation.setSource("mock-source");
        citation.setTitle("Mock Citation");
        citation.setLocator("mock-locator");
        return java.util.List.of(citation);
    }

    private ToolResponse errorResponse(String toolCode, String errorCode,
                                       String errorMessage, long startTime) {
        ToolResponse response = new ToolResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        response.setDurationMs(System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * 审计落库（旁路：失败只记日志，不影响主流程）
     */
    private void recordAudit(String toolCode, ToolRequest request, ToolResponse response,
                             ApprovalInfo approvalInfo, long startTime) {
        try {
            ToolCall call = new ToolCall();
            call.setTraceId(request.getTraceId());
            call.setSessionId(request.getSessionId());
            call.setAgentCode(request.getAgentCode());
            call.setToolCode(toolCode);
            call.setSafetyLevel(toolRegistry.getTool(toolCode)
                    .map(def -> def.getSafetyLevel() == null ? null : def.getSafetyLevel().name())
                    .orElse(null));
            call.setArguments(toJson(request.getArguments()));
            call.setSuccess(Boolean.TRUE.equals(response.getSuccess()) ? 1 : 0);
            call.setErrorCode(response.getErrorCode());
            call.setDurationMs(response.getDurationMs() != null
                    ? response.getDurationMs() : System.currentTimeMillis() - startTime);
            call.setApprovalChoice(approvalInfo.getChoice());
            call.setApprovalBy(approvalInfo.getDecidedBy());
            call.setApprovalTime(approvalInfo.getDecideTime());
            call.setChannel(channelOf(request));
            auditService.record(call);
        } catch (Exception e) {
            log.error("组装工具调用审计失败: traceId={}, tool={}", request.getTraceId(), toolCode, e);
        }
    }

    private String channelOf(ToolRequest request) {
        return request.getChannel() == null || request.getChannel().isBlank()
                ? "chat" : request.getChannel();
    }

    private String toJson(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Data
    private static class ApprovalInfo {
        private String choice;
        private Long decidedBy;
        private LocalDateTime decideTime;
    }
}
