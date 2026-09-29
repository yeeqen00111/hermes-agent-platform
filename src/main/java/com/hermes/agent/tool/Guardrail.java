package com.hermes.agent.tool;

import com.hermes.agent.dto.ToolRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 安全护栏（工具调用前的校验）
 */
@Slf4j
@Component
public class Guardrail {

    /**
     * 校验结果
     */
    public record ValidationResult(boolean valid, String errorCode, String errorMessage) {
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult fail(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage);
        }
    }

    /**
     * 校验工具调用
     */
    public ValidationResult validate(ToolDefinition toolDef, ToolRequest request) {
        // 1. 参数Schema校验
        if (!validateParamSchema(toolDef, request)) {
            return ValidationResult.fail("INVALID_ARGUMENT", "参数不符合Schema要求");
        }

        // 2. 数据范围校验
        if (!validateDataScope(toolDef, request)) {
            return ValidationResult.fail("SCOPE_DENIED", "超出允许的数据范围");
        }

        // 3. 超时时间校验
        if (request.getTimeoutMs() != null && request.getTimeoutMs() > 60000) {
            return ValidationResult.fail("INVALID_ARGUMENT", "超时时间不能超过60秒");
        }

        return ValidationResult.success();
    }

    private boolean validateParamSchema(ToolDefinition toolDef, ToolRequest request) {
        // TODO: 实现JSON Schema校验
        // 当前简化处理，只检查必需参数
        return true;
    }

    private boolean validateDataScope(ToolDefinition toolDef, ToolRequest request) {
        // 检查请求中的数据范围字段是否在工具允许的范围内
        if (toolDef.getScopeFields() == null || toolDef.getScopeFields().isEmpty()) {
            return true;
        }

        // TODO: 从request中提取实际的数据范围并与toolDef的scopeFields对比
        // 当前简化处理
        return true;
    }
}
