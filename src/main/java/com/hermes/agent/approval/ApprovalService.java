package com.hermes.agent.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.common.enums.ApprovalChoice;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.entity.ApprovalRequest;
import com.hermes.agent.entity.ApprovalWhitelist;
import com.hermes.agent.mapper.ApprovalRequestMapper;
import com.hermes.agent.mapper.ApprovalWhitelistMapper;
import com.hermes.agent.tool.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 审批门服务（契约 §3.3）。
 * 三种放行来源：白名单(allow_always，跨会话持久) > 会话级放行(allow_session) > 逐次应答(allow_once)。
 * 超时与撤回都不放行，且必须回调 onApprovalCancel 让前端撤卡片。
 */
@Slf4j
@Service
public class ApprovalService {

    /** 可「始终允许」的幂等写工具：重复执行无副作用 */
    private static final Set<String> IDEMPOTENT_WRITE_TOOLS = Set.of(
            "alert.acknowledge", "alert.resolve");

    private final ApprovalRequestMapper requestMapper;
    private final ApprovalWhitelistMapper whitelistMapper;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;

    /** 等待人工应答的调用线程，key=requestId */
    private final Map<String, CompletableFuture<ApprovalChoice>> waiters = new ConcurrentHashMap<>();

    /** 会话级放行，key=sessionId|toolCode，进程内有效 */
    private final Set<String> sessionGrants = ConcurrentHashMap.newKeySet();

    public ApprovalService(ApprovalRequestMapper requestMapper,
                           ApprovalWhitelistMapper whitelistMapper,
                           ObjectMapper objectMapper,
                           @Value("${hermes.agent.approval-timeout:300}") long timeoutSeconds) {
        this.requestMapper = requestMapper;
        this.whitelistMapper = whitelistMapper;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * 是否免审：白名单或本会话已放行
     */
    public boolean isPreApproved(String toolCode, String sessionId, Long userId) {
        return isSessionGranted(sessionId, toolCode) || isWhitelisted(toolCode, userId);
    }

    public boolean isSessionGranted(String sessionId, String toolCode) {
        return sessionId != null && sessionGrants.contains(sessionKey(sessionId, toolCode));
    }

    public boolean isWhitelisted(String toolCode, Long userId) {
        long uid = userId == null ? 0L : userId;
        Long count = whitelistMapper.selectCount(new LambdaQueryWrapper<ApprovalWhitelist>()
                .eq(ApprovalWhitelist::getToolCode, toolCode)
                .eq(ApprovalWhitelist::getEnabled, 1)
                .and(w -> w.eq(ApprovalWhitelist::getUserId, 0L).or().eq(ApprovalWhitelist::getUserId, uid)));
        return count != null && count > 0;
    }

    /**
     * 建单：落库 PENDING，返回给 sink 推卡片
     */
    public ApprovalRequest create(ToolDefinition toolDef, ToolRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ApprovalRequest row = new ApprovalRequest();
        row.setRequestId(UUID.randomUUID().toString().replace("-", ""));
        row.setSessionId(request.getSessionId());
        row.setAgentCode(request.getAgentCode());
        row.setTraceId(request.getTraceId());
        row.setUserId(request.getUserId());
        row.setToolCode(toolDef.getToolCode());
        row.setToolName(toolDef.getDisplayName());
        row.setSafetyLevel(toolDef.getSafetyLevel() == null ? null : toolDef.getSafetyLevel().name());
        row.setArguments(sanitize(toJson(request.getArguments())));
        row.setStatus("PENDING");
        row.setCreateTime(now);
        row.setExpireTime(now.plusSeconds(timeoutSeconds));
        requestMapper.insert(row);
        return row;
    }

    /**
     * 阻塞等待人工应答；超时按撤回处理并拒绝工具调用
     */
    public ApprovalChoice await(String requestId) {
        CompletableFuture<ApprovalChoice> future = new CompletableFuture<>();
        waiters.put(requestId, future);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            ApprovalChoice applied = apply(requestId, ApprovalChoice.TIMEOUT, null,
                    "审批超时(" + timeoutSeconds + "s)自动撤回");
            return applied == null ? ApprovalChoice.TIMEOUT : applied;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ApprovalChoice applied = apply(requestId, ApprovalChoice.CANCELLED, null, "等待线程被中断");
            return applied == null ? ApprovalChoice.CANCELLED : applied;
        } catch (Exception e) {
            ApprovalChoice applied = apply(requestId, ApprovalChoice.CANCELLED, null, e.getMessage());
            return applied == null ? ApprovalChoice.CANCELLED : applied;
        } finally {
            waiters.remove(requestId);
        }
    }

    /**
     * 人工应答。choice 为空或非法一律按 DENY。
     *
     * @return 实际生效的应答；null 表示该单已终结（超时/重复应答），本次应答被忽略
     */
    public ApprovalChoice respond(String requestId, String rawChoice, Long decidedBy, String reason) {
        ApprovalRequest row = findByRequestId(requestId);
        if (row == null) {
            log.warn("审批单不存在: {}", requestId);
            return null;
        }
        if (!"PENDING".equals(row.getStatus())) {
            log.warn("审批单已终结，忽略应答: {} status={}", requestId, row.getStatus());
            return null;
        }
        ApprovalChoice choice = ApprovalChoice.fromString(rawChoice);
        // 幂等边界（§12.1）：「始终允许」只对幂等写操作开放，其余降级为「允许一次」，
        // 防止 alert.suppress / notification.send 这类会掩盖问题或重复触发的操作进白名单
        if (choice == ApprovalChoice.ALLOW_ALWAYS && !IDEMPOTENT_WRITE_TOOLS.contains(row.getToolCode())) {
            log.warn("工具 {} 非幂等写操作，allow_always 降级为 allow_once（requestId={}）",
                    row.getToolCode(), requestId);
            choice = ApprovalChoice.ALLOW_ONCE;
        }
        ApprovalChoice applied = apply(requestId, choice, decidedBy, reason);
        if (applied == null) {
            return null;
        }
        if (applied == ApprovalChoice.ALLOW_SESSION) {
            grantSession(row.getSessionId(), row.getToolCode());
        } else if (applied == ApprovalChoice.ALLOW_ALWAYS) {
            grantWhitelist(row.getToolCode(), row.getUserId(), row.getAgentCode(), decidedBy);
        }
        return applied;
    }

    /**
     * 主动撤回（会话中断、用户点了停止）
     */
    public ApprovalChoice cancel(String requestId, String reason) {
        return apply(requestId, ApprovalChoice.CANCELLED, null, reason);
    }

    public List<ApprovalRequest> pending(String sessionId, Long userId) {
        return requestMapper.selectList(new LambdaQueryWrapper<ApprovalRequest>()
                .eq(ApprovalRequest::getStatus, "PENDING")
                .eq(sessionId != null && !sessionId.isBlank(), ApprovalRequest::getSessionId, sessionId)
                .eq(userId != null, ApprovalRequest::getUserId, userId)
                .orderByDesc(ApprovalRequest::getCreateTime));
    }

    public ApprovalRequest findByRequestId(String requestId) {
        return requestMapper.selectOne(new LambdaQueryWrapper<ApprovalRequest>()
                .eq(ApprovalRequest::getRequestId, requestId)
                .last("LIMIT 1"));
    }

    public List<ApprovalWhitelist> listWhitelist() {
        return whitelistMapper.selectList(new LambdaQueryWrapper<ApprovalWhitelist>()
                .orderByDesc(ApprovalWhitelist::getGrantTime));
    }

    public void revokeWhitelist(Long id) {
        ApprovalWhitelist update = new ApprovalWhitelist();
        update.setId(id);
        update.setEnabled(0);
        whitelistMapper.updateById(update);
    }

    private ApprovalChoice apply(String requestId, ApprovalChoice choice, Long decidedBy, String reason) {
        int rows = requestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequest>()
                .eq(ApprovalRequest::getRequestId, requestId)
                .eq(ApprovalRequest::getStatus, "PENDING")
                .set(ApprovalRequest::getStatus, choice.toStatus())
                .set(ApprovalRequest::getChoice, choice.name().toLowerCase())
                .set(ApprovalRequest::getDecidedBy, decidedBy)
                .set(ApprovalRequest::getReason, reason)
                .set(ApprovalRequest::getDecideTime, LocalDateTime.now()));
        if (rows == 0) {
            return null;
        }
        log.info("审批单 {} 终结: choice={}, by={}, reason={}", requestId, choice, decidedBy, reason);
        CompletableFuture<ApprovalChoice> future = waiters.remove(requestId);
        if (future != null) {
            future.complete(choice);
        }
        return choice;
    }

    private void grantSession(String sessionId, String toolCode) {
        if (sessionId == null) {
            return;
        }
        sessionGrants.add(sessionKey(sessionId, toolCode));
    }

    private void grantWhitelist(String toolCode, Long userId, String agentCode, Long grantedBy) {
        long uid = userId == null ? 0L : userId;
        ApprovalWhitelist existing = whitelistMapper.selectOne(new LambdaQueryWrapper<ApprovalWhitelist>()
                .eq(ApprovalWhitelist::getToolCode, toolCode)
                .eq(ApprovalWhitelist::getUserId, uid)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setEnabled(1);
            existing.setAgentCode(agentCode);
            existing.setGrantedBy(grantedBy == null ? 0L : grantedBy);
            existing.setGrantTime(LocalDateTime.now());
            whitelistMapper.updateById(existing);
            return;
        }
        ApprovalWhitelist row = new ApprovalWhitelist();
        row.setToolCode(toolCode);
        row.setUserId(uid);
        row.setAgentCode(agentCode);
        row.setGrantedBy(grantedBy == null ? 0L : grantedBy);
        row.setGrantTime(LocalDateTime.now());
        row.setEnabled(1);
        whitelistMapper.insert(row);
    }

    private String sessionKey(String sessionId, String toolCode) {
        return sessionId + "|" + toolCode;
    }

    private String toJson(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 参数里可能带连接串，凭据部分一律打码后再落库/推前端 */
    private String sanitize(String text) {
        return text == null ? null : text.replaceAll("://[^@\\s]+@", "://***@");
    }
}
