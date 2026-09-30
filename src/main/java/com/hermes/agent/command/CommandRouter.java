package com.hermes.agent.command;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiCommandBundle;
import com.hermes.agent.entity.Skill;
import com.hermes.agent.mapper.AiCommandBundleMapper;
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
    private final ObjectMapper objectMapper;

    private final Map<String, CommandHandler> builtinCommands = new ConcurrentHashMap<>();

    @PostConstruct
    private void registerBuiltinCommands() {
        builtinCommands.put("new", args -> CommandResult.success("已创建新会话"));
        builtinCommands.put("stop", args -> CommandResult.success("已停止当前生成"));
        builtinCommands.put("help", args -> CommandResult.success("""
                可用指令：
                /new - 创建新会话
                /stop - 停止生成
                /help - 显示帮助
                /agents - 列出可用Agent
                /model - 切换模型
                /skills - 列出已加载技能
                /commands - 列出所有指令
                /context - 显示上下文占用
                """));
        builtinCommands.put("agents", args -> CommandResult.success("TODO: 列出可用Agent"));
        builtinCommands.put("model", args -> CommandResult.success("TODO: 切换模型"));
        builtinCommands.put("skills", args -> {
            String index = skillRegistry.indexBlock();
            return CommandResult.success(index.isBlank()
                    ? "无已加载技能"
                    : "已加载技能：\n" + index);
        });
        builtinCommands.put("commands", args -> CommandResult.success(listAllCommands()));
        builtinCommands.put("context", args -> CommandResult.success("TODO: 显示上下文占用"));
    }

    /**
     * 解析并执行指令
     *
     * @return 指令命中返回处理结果；routeToChat=true 表示技能/捆绑包指令，正文需并进本轮走对话
     */
    public CommandResult route(String input) {
        if (input == null || !input.trim().startsWith("/")) {
            return null; // 不是指令，走正常对话流程
        }

        String command = extractCommand(input);
        String args = extractArgs(input);

        // 1. 内置指令优先，不可被技能覆盖（§7.2）
        CommandHandler handler = builtinCommands.get(command);
        if (handler != null) {
            log.info("执行内置指令: /{}", command);
            return handler.handle(args);
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
        CommandResult handle(String args);
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
