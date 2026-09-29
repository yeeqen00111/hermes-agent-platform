package com.hermes.agent.tool;

import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    /**
     * 执行工具调用
     */
    public ToolResponse execute(String toolCode, ToolRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. 查找工具定义
        Optional<ToolDefinition> toolOpt = toolRegistry.getTool(toolCode);
        if (toolOpt.isEmpty()) {
            return errorResponse(toolCode, "TOOL_NOT_FOUND", "工具不存在: " + toolCode, startTime);
        }

        ToolDefinition toolDef = toolOpt.get();

        // 2. Guardrail校验
        var validationResult = guardrail.validate(toolDef, request);
        if (!validationResult.isValid()) {
            return errorResponse(toolCode, validationResult.getErrorCode(),
                    validationResult.getErrorMessage(), startTime);
        }

        // 3. WRITE级工具需要审批
        if (toolDef.getSafetyLevel().ordinal() >= com.hermes.agent.common.enums.SafetyLevel.WRITE.ordinal()) {
            // TODO: 触发审批流程，当前返回待审批
            log.warn("WRITE级工具 {} 需要审批，暂未实现审批流程", toolCode);
            return errorResponse(toolCode, "APPROVAL_REQUIRED",
                    "该操作需要人工审批", startTime);
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
