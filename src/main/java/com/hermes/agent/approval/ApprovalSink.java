package com.hermes.agent.approval;

import com.hermes.agent.entity.ApprovalRequest;

/**
 * 审批交互出口：由能与人对话的调用方（Chat SSE）实现。
 * 无出口的调用方（固定流程、定时任务）传 null，ToolExecutor 保持 fail-closed 直接拒绝。
 */
public interface ApprovalSink {

    /** 向用户抛出审批卡片 */
    void onApprovalRequest(ApprovalRequest request);

    /** 撤回卡片：超时、取消或已被应答，前端必须处理否则卡片会永远挂着 */
    void onApprovalCancel(String requestId, String reason);
}
