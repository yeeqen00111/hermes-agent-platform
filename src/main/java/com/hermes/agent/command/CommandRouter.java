package com.hermes.agent.command;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 斜杠指令路由器
 */
@Slf4j
@Component
public class CommandRouter {

    private final Map<String, CommandHandler> builtinCommands = new ConcurrentHashMap<>();

    public CommandRouter() {
        // 注册内置指令
        registerBuiltinCommands();
    }

    /**
     * 解析并执行指令
     * @return 如果是指令返回处理结果，否则返回null表示普通对话
     */
    public CommandResult route(String input) {
        if (input == null || !input.trim().startsWith("/")) {
            return null; // 不是指令，走正常对话流程
        }

        String command = extractCommand(input);
        String args = extractArgs(input);

        // 1. 先查内置指令
        CommandHandler handler = builtinCommands.get(command);
        if (handler != null) {
            log.info("执行内置指令: /{}", command);
            return handler.handle(args);
        }

        // 2. TODO: 查技能指令和捆绑包指令

        // 3. 未知指令
        log.warn("未知指令: /{}", command);
        return CommandResult.error("UNKNOWN_COMMAND", "未知指令: /" + command + "，输入 /help 查看可用指令");
    }

    /**
     * 注册内置指令
     */
    private void registerBuiltinCommands() {
        builtinCommands.put("new", args -> {
            return CommandResult.success("已创建新会话");
        });

        builtinCommands.put("stop", args -> {
            return CommandResult.success("已停止当前生成");
        });

        builtinCommands.put("help", args -> {
            String help = """
                    可用指令：
                    /new - 创建新会话
                    /stop - 停止生成
                    /help - 显示帮助
                    /agents - 列出可用Agent
                    /model - 切换模型
                    /skills - 列出已加载技能
                    /commands - 列出所有指令
                    /context - 显示上下文占用
                    """;
            return CommandResult.success(help);
        });

        builtinCommands.put("agents", args -> {
            return CommandResult.success("TODO: 列出可用Agent");
        });

        builtinCommands.put("model", args -> {
            return CommandResult.success("TODO: 切换模型");
        });

        builtinCommands.put("skills", args -> {
            return CommandResult.success("TODO: 列出已加载技能");
        });

        builtinCommands.put("commands", args -> {
            return CommandResult.success("TODO: 列出所有指令");
        });

        builtinCommands.put("context", args -> {
            return CommandResult.success("TODO: 显示上下文占用");
        });
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

        public static CommandResult success(String message) {
            CommandResult result = new CommandResult();
            result.setSuccess(true);
            result.setMessage(message);
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
