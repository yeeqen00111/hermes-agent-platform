package com.hermes.agent.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AiModel;
import com.hermes.agent.entity.ModelProvider;
import com.hermes.agent.mapper.AiModelMapper;
import com.hermes.agent.mapper.ModelProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型目录（契约 §3.5 {@code GET /api/models}）：只读，供身份包选型与平台配置展示。
 *
 * <p>密钥只存引用（{@code ai_model_provider.api_key_ref} 为环境变量名），库中无明文——ADR-005。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ModelController {

    private final AiModelMapper modelMapper;
    private final ModelProviderMapper providerMapper;

    /**
     * 可用模型。契约要求「AI 回答必须用支持工具调用的模型」，故提供 {@code toolsOnly} 过滤。
     *
     * @param providerCode    按供应商过滤（可选）
     * @param toolsOnly       仅返回支持工具调用的模型
     * @param includeDisabled 是否包含停用项（默认 false，只返回启用项）
     */
    @GetMapping("/models")
    public List<AiModel> models(@RequestParam(required = false) String providerCode,
                                @RequestParam(required = false, defaultValue = "false") boolean toolsOnly,
                                @RequestParam(required = false, defaultValue = "false") boolean includeDisabled) {
        LambdaQueryWrapper<AiModel> q = new LambdaQueryWrapper<>();
        if (providerCode != null && !providerCode.isBlank()) {
            q.eq(AiModel::getProviderCode, providerCode.trim());
        }
        if (!includeDisabled) {
            q.eq(AiModel::getEnabled, 1);
        }
        if (toolsOnly) {
            q.eq(AiModel::getSupportsTools, 1);
        }
        q.orderByAsc(AiModel::getProviderCode).orderByAsc(AiModel::getModelName);
        return modelMapper.selectList(q);
    }

    /**
     * 模型供应商（{@code apiKeyRef} 为环境变量名/密钥管理器键，非明文）。
     */
    @GetMapping("/model-providers")
    public List<ModelProvider> providers(
            @RequestParam(required = false, defaultValue = "false") boolean includeDisabled) {
        LambdaQueryWrapper<ModelProvider> q = new LambdaQueryWrapper<>();
        if (!includeDisabled) {
            q.eq(ModelProvider::getEnabled, 1);
        }
        q.orderByAsc(ModelProvider::getProviderCode);
        return providerMapper.selectList(q);
    }
}
