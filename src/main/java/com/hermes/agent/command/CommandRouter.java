package com.hermes.agent.command;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AiCommandBundle;
import com.hermes.agent.entity.AiModel;
import com.hermes.agent.entity.Skill;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiCommandBundleMapper;
import com.hermes.agent.mapper.AiModelMapper;
import com.hermes.agent.session.SessionManager;
import com.hermes.agent.skill.SkillRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 斜杠指令路由器（§7.2 解析顺序：内置 → 技能/捆绑包 → 未知）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandRouter {

    private final SkillRegistry skillRegistry;
    private final AiCommandBundleMapper bundleMapper;
    private final AgentProfileMapper profileMapper;
    private final AiModelMapper modelMapper;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    private final Map<String, CommandHandler> builtinCommands = new ConcurrentHashMap<>();

    /** 指令执行上下文：/model /context 需要知道当前会话与 Agent */
    public record CommandContext(String sessionId, String agentCode, Long userId) {
    }

    @PostConstruct
    private void registerBuiltinCommands() {
        builtinCommands.put("new", (args, ctx) -> CommandResult.success("已创建新会话"));
        builtinCommands.put("stop", (args, ctx) -> CommandResult.success("已停止当前生成"));
        builtinCommands.put("help", (args, ctx) -> CommandResult.success("""
                可用指令：
                /new - 创建新会话
                /stop - 停止生成
                /help - 显示帮助
                /agents - 列出可用Agent
                /model - 查看/切换本会话模型（/model provider/model，/model reset 恢复默认）
                /skills - 列出已加载技能
                /commands - 列出所有指令
                /context - 显示上下文占用
                """));
        builtinCommands.put("agents", (args, ctx) -> CommandResult.success(listAgents()));
        builtinCommands.put("model", (args, ctx) -> handleModelCommand(args, ctx));
        builtinCommands.put("skills", (args, ctx) -> {
            String index = skillRegistry.indexBlock();
            return CommandResult.success(index.isBlank()
                    ? "无已加载技能"
                    : "已加载技能：\n" + index);
        });
        builtinCommands.put("commands", (args, ctx) -> CommandResult.success(listAllCommands()));
        builtinCommands.put("context", (args, ctx) -> handleContextCommand(ctx));
    }

    /**
     * 解析并执行指令（无会话上下文，/model /context 会降级）
     *
     * @return 指令命中返回处理结果；routeToChat=true 表示技能/捆绑包指令，正文需并进本轮走对话
     */
    public CommandResult route(String input) {
        return route(input, null);
    }

    /**
     * 带会话上下文的路由：ChatController / ChannelEventService 调用
     */
    public CommandResult route(String input, CommandContext context) {
        if (input == null || !input.trim().startsWith("/")) {
            return null; // 不是指令，走正常对话流程
        }

        String command = extractCommand(input);
        String args = extractArgs(input);

        // 1. 内置指令优先，不可被技能覆盖（§7.2）
        CommandHandler handler = builtinCommands.get(command);
        if (handler != null) {
            log.info("执行内置指令: /{}", command);
            return handler.handle(args, context);
        }

        // 2. 技能指令：同名技能正文并进本轮（§7.1）
        Optional<String> skillContent = skillRegistry.content(command);
        if (skillContent.isPresent()) {
            log.info("命中技能指令: /{}", command);
            return CommandResult.routeToChat(skillContent.get());
        }

        // 3. 捆绑包指令：预载一串技能（§7.4）
        String bundleContent = bundleContent(command);
        if (bundleContent != null) {
            log.info("命中捆绑包指令: /{}", command);
            return CommandResult.routeToChat(bundleContent);
        }

        // 4. 未知指令：给近似建议（模糊匹配技能指令名）
        log.warn("未知指令: /{}", command);
        String suggestion = suggest(command);
        String hint = "未知指令: /" + command + "，输入 /help 查看可用指令";
        if (!suggestion.isBlank()) {
            hint += "。你是想用 /" + suggestion + " 吗？";
        }
        return CommandResult.error("UNKNOWN_COMMAND", hint);
    }

    private String listAgents() {
        List<AgentProfile> profiles = profileMapper.selectList(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getStatus, "PUBLISHED")
                .orderByAsc(AgentProfile::getAgentCode));
        if (profiles.isEmpty()) {
            return "无已发布 Agent";
        }
        return "可用 Agent：\n" + profiles.stream()
                .map(p -> "- " + p.getAgentCode() + "：" + p.getName()
                        + "（" + p.getModelProvider() + "/" + p.getModelName()
                        + (p.getExecutionMode() == null ? "" : "，" + p.getExecutionMode()) + "）")
                .collect(Collectors.joining("\n"));
    }

    private CommandResult handleModelCommand(String args, CommandContext ctx) {
        List<AiModel> models = modelMapper.selectList(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getEnabled, 1)
                .orderByAsc(AiModel::getProviderCode));
        String catalog = models.isEmpty() ? "（模型目录为空）"
                : models.stream().map(m -> m.getProviderCode() + "/" + m.getModelName())
                        .collect(Collectors.joining("、"));

        if (args == null || args.isBlank()) {
            return CommandResult.success("当前模型：" + currentModelDesc(ctx)
                    + "\n可用模型：" + catalog
                    + "\n用法：/model <provider/model> 切换（仅本会话）；/model reset 恢复默认");
        }
        String wanted = args.trim();
        if (ctx == null || ctx.sessionId() == null) {
            return CommandResult.error("NO_SESSION", "无会话上下文，无法切换模型");
        }
        if ("reset".equalsIgnoreCase(wanted)) {
            sessionManager.setModelOverride(ctx.sessionId(), null);
            return CommandResult.success("已恢复 Agent 默认模型：" + currentModelDesc(ctx));
        }
        AiModel match = models.stream()
                .filter(m -> (m.getProviderCode() + "/" + m.getModelName()).equalsIgnoreCase(wanted)
                        || m.getModelName().equalsIgnoreCase(wanted))
                .findFirst().orElse(null);
        if (match == null) {
            return CommandResult.error("UNKNOWN_MODEL",
                    "未知模型: " + wanted + "。可用模型：" + catalog);
        }
        sessionManager.setModelOverride(ctx.sessionId(),
                match.getProviderCode() + "/" + match.getModelName());
        return CommandResult.success("本会话已切换模型：" + match.getProviderCode() + "/" + match.getModelName());
    }

    private String currentModelDesc(CommandContext ctx) {
        if (ctx != null && ctx.sessionId() != null) {
            String override = sessionManager.getSession(ctx.sessionId())
                    .map(SessionManager.ChatSession::getModelOverride)
                    .filter(o -> o != null && !o.isBlank())
                    .orElse(null);
            if (override != null) {
                return override + "（会话级覆盖）";
            }
        }
        if (ctx != null && ctx.agentCode() != null) {
            AgentProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                    .eq(AgentProfile::getAgentCode, ctx.agentCode()));
            if (profile != null) {
                return profile.getModelProvider() + "/" + profile.getModelName() + "（Agent 默认）";
            }
        }
        return "未知（无会话与 Agent 上下文）";
    }

    private CommandResult handleContextCommand(CommandContext ctx) {
        if (ctx == null || ctx.sessionId() == null) {
            return CommandResult.error("NO_SESSION", "无会话上下文");
        }
        String sessionId = ctx.sessionId();
        List<SessionManager.ChatMessage> history = sessionManager.getHistory(sessionId, 500);
        long totalChars = history.stream()
                .mapToInt(m -> m.getContent() == null ? 0 : m.getContent().length())
                .sum();
        String model = sessionManager.getSession(sessionId)
                .map(SessionManager.ChatSession::getModelOverride)
                .filter(o -> o != null && !o.isBlank())
                .orElseGet(() -> profileModel(ctx));
        Integer window = null;
        if (model != null) {
            String modelName = model.contains("/") ? model.substring(model.indexOf('/') + 1) : model;
            AiModel m = modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                    .eq(AiModel::getModelName, modelName)
                    .eq(AiModel::getEnabled, 1)
                    .last("limit 1"));
            window = m == null ? null : m.getContextWindow();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("会话 ").append(sessionId).append(" 上下文占用：\n");
        sb.append("- 历史消息：").append(history.size()).append(" 条，合计 ")
                .append(totalChars).append(" 字符\n");
        if (model != null) {
            sb.append("- 当前模型：").append(model);
            if (window != null && window > 0) {
                sb.append("，上下文窗口 ").append(window)
                        .append("（约 ").append(window / 4).append(" tokens）");
            }
            sb.append('\n');
        }
        sb.append("- 提示：历史按窗口回读，系统提示与技能索引每轮重新装配");
        return CommandResult.success(sb.toString());
    }

    private String profileModel(CommandContext ctx) {
        if (ctx == null || ctx.agentCode() == null) {
            return null;
        }
        AgentProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getAgentCode, ctx.agentCode()));
        return profile == null ? null : profile.getModelProvider() + "/" + profile.getModelName();
    }

    private String listAllCommands() {
        StringBuilder sb = new StringBuilder("内置指令：/new /stop /help /agents /model /skills /commands /context\n");
        List<Skill> skills = skillRegistry.enabledSkills();
        if (!skills.isEmpty()) {
            sb.append("技能指令：")
                    .append(skills.stream().map(s -> "/" + s.getSkillCode()).collect(Collectors.joining(" ")))
                    .append('\n');
        }
        List<AiCommandBundle> bundles = bundleMapper.selectList(new LambdaQueryWrapper<AiCommandBundle>()
                .eq(AiCommandBundle::getStatus, "ENABLED"));
        if (!bundles.isEmpty()) {
            sb.append("捆绑包指令：")
                    .append(bundles.stream().map(b -> "/" + b.getBundleCode()).collect(Collectors.joining(" ")))
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * 捆绑包正文：清单里每个技能取当前版本全文拼接；没有可用正文时返回 null 走未知指令
     */
    private String bundleContent(String command) {
        AiCommandBundle bundle = bundleMapper.selectOne(new LambdaQueryWrapper<AiCommandBundle>()
                .eq(AiCommandBundle::getBundleCode, command)
                .eq(AiCommandBundle::getStatus, "ENABLED"));
        if (bundle == null || bundle.getSkillCodes() == null || bundle.getSkillCodes().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(bundle.getSkillCodes());
            List<String> parts = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(n -> skillRegistry.content(n.asText()).ifPresent(parts::add));
            }
            return parts.isEmpty() ? null : String.join("\n\n---\n\n", parts);
        } catch (Exception e) {
            log.warn("捆绑包技能清单解析失败: {}", bundle.getBundleCode(), e);
            return null;
        }
    }

    private String suggest(String command) {
        return skillRegistry.enabledSkills().stream()
                .map(Skill::getSkillCode)
                .filter(code -> code.startsWith(command) || command.startsWith(code))
                .findFirst().orElse("");
    }

    private String extractCommand(String input) {
        String trimmed = input.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex == -1) {
            return trimmed.substring(1); // 去掉 /
        }
        return trimmed.substring(1, spaceIndex);
    }

    private String extractArgs(String input) {
        String trimmed = input.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex == -1) {
            return "";
        }
        return trimmed.substring(spaceIndex + 1).trim();
    }

    @FunctionalInterface
    public interface CommandHandler {
        CommandResult handle(String args, CommandContext context);
    }

    @Data
    public static class CommandResult {
        private boolean success;
        private String message;
        private String errorCode;
        private Object data;
        /** true 表示命中技能/捆绑包指令：context 需拼到本轮用户消息前，走正常对话 */
        private boolean routeToChat;
        private String context;

        public static CommandResult success(String message) {
            CommandResult result = new CommandResult();
            result.setSuccess(true);
            result.setMessage(message);
            return result;
        }

        public static CommandResult routeToChat(String context) {
            CommandResult result = new CommandResult();
            result.setSuccess(true);
            result.setRouteToChat(true);
            result.setContext(context);
            return result;
        }

        public static CommandResult error(String errorCode, String message) {
            CommandResult result = new CommandResult();
            result.setSuccess(false);
            result.setErrorCode(errorCode);
            result.setMessage(message);
            return result;
        }
    }
}
