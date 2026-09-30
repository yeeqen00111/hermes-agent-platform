package com.hermes.agent.tool;

import com.hermes.agent.approval.ApprovalService;
import com.hermes.agent.approval.ApprovalSink;
import com.hermes.agent.common.enums.ApprovalChoice;
import com.hermes.agent.common.enums.SafetyLevel;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.entity.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 工具执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final Guardrail guardrail;
    private final ApprovalService approvalService;

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
            ToolResponse rejected = passApprovalGate(toolDef, request, approvalSink, startTime);
            if (rejected != null) {
                return rejected;
            }
        }

        // 4. 执行工具（当前用Mock）
        try {
            return mockExecute(toolCode, request, startTime);
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolCode, e);
            return errorResponse(toolCode, "EXECUTION_ERROR",
                    "工具执行失败: " + e.getMessage(), startTime);
        }
    }

    private boolean requiresApproval(ToolDefinition toolDef) {
        SafetyLevel level = toolDef.getSafetyLevel();
        return level == SafetyLevel.WRITE || level == SafetyLevel.CONTROLLED;
    }

    /**
     * 审批门：白名单/会话放行直接过；否则建单→推卡片→阻塞等应答。
     *
     * @return null 表示放行，非 null 为拒绝响应
     */
    private ToolResponse passApprovalGate(ToolDefinition toolDef, ToolRequest request,
                                          ApprovalSink sink, long startTime) {
        String toolCode = toolDef.getToolCode();
        if (approvalService.isPreApproved(toolCode, request.getSessionId(), request.getUserId())) {
            log.info("工具 {} 命中免审（白名单或会话级放行）", toolCode);
            return null;
        }
        if (sink == null) {
            log.warn("工具 {} 需人工审批，但调用方无审批交互能力，拒绝执行", toolCode);
            return errorResponse(toolCode, "APPROVAL_REQUIRED",
                    "该操作需要人工审批，当前调用渠道不支持审批交互", startTime);
        }

        ApprovalRequest card = approvalService.create(toolDef, request);
        sink.onApprovalRequest(card);
        ApprovalChoice choice = approvalService.await(card.getRequestId());
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
}
