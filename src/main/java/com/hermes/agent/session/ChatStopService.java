package com.hermes.agent.session;

import com.hermes.agent.approval.ApprovalService;
import com.hermes.agent.runtime.SessionCancellationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 会话停止编排（#54）：进程内取消 + 取消待审批卡 + 库置 STOPPED。
 * /stop、/new、interrupt API 共用这一条链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStopService {

    private final SessionCancellationRegistry cancellationRegistry;
    private final SessionManager sessionManager;
    private final ApprovalService approvalService;

    /**
     * 停止该会话的运行与待审批；返回是否取消了正在进行的运行轮
     */
    public boolean stopRun(String sessionId, String reason) {
        boolean interruptedRun = cancellationRegistry.cancel(sessionId);
        approvalService.pending(sessionId, null)
                .forEach(card -> approvalService.cancel(card.getRequestId(), reason));
        sessionManager.stopSession(sessionId);
        log.info("停止会话链路执行完成: {}, 有在跑轮: {}", sessionId, interruptedRun);
        return interruptedRun;
    }
}
