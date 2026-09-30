package com.hermes.agent.api;

import com.hermes.agent.approval.ApprovalService;
import com.hermes.agent.common.enums.ApprovalChoice;
import com.hermes.agent.entity.ApprovalRequest;
import com.hermes.agent.entity.ApprovalWhitelist;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审批管理（契约 §3.3）：前端刷新后恢复卡片、应答、白名单维护
 */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * 查询挂起的审批（前端刷新后恢复卡片用）
     */
    @GetMapping("/pending")
    public List<ApprovalRequest> pending(@RequestParam(required = false) String sessionId,
                                         @RequestParam(required = false) Long userId) {
        return approvalService.pending(sessionId, userId);
    }

    @GetMapping("/{requestId}")
    public ApprovalRequest detail(@PathVariable String requestId) {
        return approvalService.findByRequestId(requestId);
    }

    /**
     * 应答。choice 为空或非法一律按拒绝处理；单子已终结（超时/重复应答）时 ignored=true
     */
    @PostMapping("/{requestId}/respond")
    public Map<String, Object> respond(@PathVariable String requestId,
                                       @RequestBody RespondRequest body,
                                       @RequestHeader(value = "X-Actor-User-Id", required = false) Long actorUserId) {
        Long decidedBy = body.getDecidedBy() != null ? body.getDecidedBy() : actorUserId;
        ApprovalChoice choice = approvalService.respond(requestId, body.getChoice(), decidedBy, body.getReason());
        if (choice == null) {
            return Map.of("success", false, "ignored", true,
                    "message", "审批单已结束或不存在，本次应答未生效");
        }
        return Map.of("success", true, "ignored", false,
                "choice", choice.name().toLowerCase(), "allowed", choice.isAllowed());
    }

    /**
     * 主动撤回（不是拒绝：撤回的是"问客户端"这个动作）
     */
    @PostMapping("/{requestId}/cancel")
    public Map<String, Object> cancel(@PathVariable String requestId,
                                      @RequestParam(required = false) String reason) {
        ApprovalChoice choice = approvalService.cancel(requestId, reason);
        return Map.of("success", choice != null, "message",
                choice == null ? "审批单已结束或不存在" : "已撤回");
    }

    @GetMapping("/whitelist")
    public List<ApprovalWhitelist> whitelist() {
        return approvalService.listWhitelist();
    }

    @DeleteMapping("/whitelist/{id}")
    public Map<String, Object> revokeWhitelist(@PathVariable Long id) {
        approvalService.revokeWhitelist(id);
        return Map.of("success", true);
    }

    @Data
    public static class RespondRequest {
        /** allow_once | allow_session | allow_always | deny */
        private String choice;
        private String reason;
        private Long decidedBy;
    }
}
