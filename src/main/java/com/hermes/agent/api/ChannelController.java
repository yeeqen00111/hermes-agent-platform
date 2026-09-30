package com.hermes.agent.api;

import com.hermes.agent.channel.ChannelEventService;
import com.hermes.agent.channel.ChannelService;
import com.hermes.agent.entity.AiChannel;
import com.hermes.agent.entity.AiChannelUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 渠道管理 API：配置 CRUD、连接心跳、用户配对、入站事件入口（对接位）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final ChannelEventService eventService;

    @Value("${hermes.channel.inbound-token:}")
    private String inboundToken;

    // ---------- 渠道配置 ----------

    @GetMapping("/api/channels")
    public List<AiChannel> listChannels() {
        return channelService.listChannels();
    }

    @PostMapping("/api/channels")
    public Map<String, Object> saveChannel(@RequestBody AiChannel channel) {
        channelService.saveChannel(channel);
        return Map.of("success", true, "message", "渠道已保存");
    }

    @DeleteMapping("/api/channels/{channelCode}")
    public Map<String, Object> deleteChannel(@PathVariable String channelCode) {
        channelService.deleteChannel(channelCode);
        return Map.of("success", true, "message", "渠道已删除");
    }

    // ---------- 连接状态 ----------

    @PostMapping("/api/channels/{channelCode}/heartbeat")
    public Map<String, Object> heartbeat(@PathVariable String channelCode,
                                         @RequestBody(required = false) HeartbeatRequest request) {
        channelService.heartbeat(channelCode,
                request == null || request.getInstanceId() == null ? "unknown" : request.getInstanceId());
        return Map.of("success", true, "message", "心跳已记录");
    }

    // ---------- 用户配对 ----------

    @GetMapping("/api/channels/{channelCode}/users")
    public List<AiChannelUser> listPairings(@PathVariable String channelCode) {
        return channelService.listPairings(channelCode);
    }

    @PostMapping("/api/channels/{channelCode}/users")
    public Map<String, Object> pair(@PathVariable String channelCode, @RequestBody PairRequest request) {
        channelService.pair(channelCode, request.getChannelUserId(),
                request.getPlatformUserId(), request.getPairedBy());
        return Map.of("success", true, "message", "配对已保存");
    }

    @DeleteMapping("/api/channels/{channelCode}/users/{channelUserId}")
    public Map<String, Object> unpair(@PathVariable String channelCode,
                                      @PathVariable String channelUserId) {
        channelService.unpair(channelCode, channelUserId);
        return Map.of("success", true, "message", "配对已解除");
    }

    // ---------- 入站事件（渠道适配器单元 / Java 平台网关推送） ----------

    @PostMapping("/api/channel/{channelCode}/events")
    public Map<String, Object> inbound(@PathVariable String channelCode,
                                       @RequestBody ChannelEventService.InboundEvent event,
                                       @RequestHeader(value = "X-Channel-Token", required = false) String token) {
        if (inboundToken != null && !inboundToken.isBlank()
                && !inboundToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "渠道入站令牌校验失败");
        }
        if (inboundToken == null || inboundToken.isBlank()) {
            log.warn("渠道入站令牌未配置，开发放行（生产必须设置 HERMES_CHANNEL_INBOUND_TOKEN）");
        }
        if (event.getChannelUserId() == null || event.getChannelUserId().isBlank()
                || event.getMessage() == null || event.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelUserId 与 message 必填");
        }
        return eventService.handleInbound(channelCode, event);
    }

    @Data
    public static class HeartbeatRequest {
        private String instanceId;
    }

    @Data
    public static class PairRequest {
        private String channelUserId;
        private Long platformUserId;
        private String pairedBy;
    }
}
