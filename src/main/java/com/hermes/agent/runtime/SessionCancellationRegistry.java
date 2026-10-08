package com.hermes.agent.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 会话运行取消注册表（#54 中断链路的进程内信号面）。
 * AgentRuntime 执行时以 sessionId 注册句柄，停止方（/stop、/new、interrupt API）在此置取消位；
 * 运行循环在轮边界、工具调用前与 LLM 返回后检查（ADR-012）。
 */
@Slf4j
@Component
public class SessionCancellationRegistry {

    /** 一次注册对应一轮运行；运行循环各检查点读取 isCancelled */
    public interface Handle {
        boolean isCancelled();
    }

    private final ConcurrentHashMap<String, Handle> running = new ConcurrentHashMap<>();

    /** 注册一轮运行；同会话已有运行时先取消旧轮（同会话并发轮以最后一轮为准） */
    public Handle register(String sessionId) {
        if (sessionId == null) {
            return () -> false;
        }
        Pin pin = new Pin();
        Handle prev = running.put(sessionId, pin);
        if (prev != null) {
            ((Pin) prev).flag.set(true);
            log.info("会话 {} 有新一轮进入，取消旧轮", sessionId);
        }
        return pin;
    }

    /** 注销仅当仍指向本轮，避免误删后进轮的句柄 */
    public void unregister(String sessionId, Handle handle) {
        if (sessionId == null || handle == null) {
            return;
        }
        running.remove(sessionId, handle);
    }

    /** 置取消位；返回是否存在在跑轮 */
    public boolean cancel(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        Handle handle = running.get(sessionId);
        if (handle instanceof Pin pin) {
            pin.flag.set(true);
            log.info("请求取消会话 {} 的运行轮", sessionId);
            return true;
        }
        return false;
    }

    public Optional<Handle> runningHandle(String sessionId) {
        return sessionId == null ? Optional.empty() : Optional.ofNullable(running.get(sessionId));
    }

    private static final class Pin implements Handle {
        private final AtomicBoolean flag = new AtomicBoolean(false);

        @Override
        public boolean isCancelled() {
            return flag.get();
        }
    }
}
