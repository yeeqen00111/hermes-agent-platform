package com.hermes.agent.tool;

import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 迁移项工具适配层（四期，migration-assessment.md §5-2）。
 * 数据面在 Java 平台（日志/告警/Nacos/知识库/问数/报表），契约待 Java 团队确认。
 * 未接通前显式报错，绝不返回假数据误导 LLM；mock 仅限开发联调显式开启。
 */
@Slf4j
@Component
public class MigrationToolAdapter {

    private final boolean mockMode;
    private final String dataPlaneBaseUrl;

    public MigrationToolAdapter(
            @Value("${hermes.tools.mock:false}") boolean mockMode,
            @Value("${hermes.migration.data-plane.base-url:}") String dataPlaneBaseUrl) {
        this.mockMode = mockMode;
        this.dataPlaneBaseUrl = dataPlaneBaseUrl;
    }

    public ToolResponse execute(String toolCode, ToolRequest request, long startTime) {
        if (mockMode) {
            log.warn("Mock执行工具 {}（hermes.tools.mock=true，仅限开发联调，数据为假）", toolCode);
            return mockResponse(toolCode, startTime);
        }
        if (dataPlaneBaseUrl.isBlank()) {
            return error(toolCode, "MIGRATION_PENDING", startTime,
                    "迁移未接通: 工具 " + toolCode + " 的数据面在 Java 平台，"
                            + "未配置 hermes.migration.data-plane.base-url（契约见 migration-assessment.md §6，待 Java 团队确认）");
        }
        return error(toolCode, "MIGRATION_CONTRACT_DRAFT", startTime,
                "迁移契约未确认: 工具 " + toolCode + " 的调用契约尚未与 Java 平台确认，"
                        + "当前实现不发起真实调用（migration-assessment.md §6 为起草形状）");
    }

    private ToolResponse mockResponse(String toolCode, long startTime) {
        ToolResponse response = new ToolResponse();
        response.setSuccess(true);
        response.setData(createMockData(toolCode));
        response.setCitations(createMockCitations(toolCode));
        response.setDurationMs(System.currentTimeMillis() - startTime);
        return response;
    }

    private Object createMockData(String toolCode) {
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

    private java.util.List<ToolResponse.Citation> createMockCitations(String toolCode) {
        var citation = new ToolResponse.Citation();
        citation.setKind("log");
        citation.setSource("mock-source");
        citation.setTitle("Mock Citation");
        citation.setLocator("mock-locator");
        return java.util.List.of(citation);
    }

    private ToolResponse error(String toolCode, String errorCode, long startTime, String message) {
        ToolResponse response = new ToolResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(message);
        response.setDurationMs(System.currentTimeMillis() - startTime);
        return response;
    }
}
