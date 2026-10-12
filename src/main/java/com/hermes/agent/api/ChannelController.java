package com.hermes.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.channel.ChannelConfigs;
import com.hermes.agent.channel.ChannelEventService;
import com.hermes.agent.channel.ChannelOutboundService;
import com.hermes.agent.channel.ChannelService;
import com.hermes.agent.channel.StreamCard;
import com.hermes.agent.channel.StreamingCardSender;
import com.hermes.agent.entity.AiChannel;
import com.hermes.agent.entity.AiChannelUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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
    private final ChannelOutboundService outboundService;
    private final ObjectMapper objectMapper;

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

    /**
     * 增量卡片试发：建卡 → 3 段模拟增量 → 收尾，返回每步结果与最终正文。
     * 用于验证渠道凭据/卡片模板/更新接口是否真的通（会真实发到 {@code receiveId}，请填测试账号）。
     */
    @PostMapping("/api/channels/{channelCode}/stream-card/demo")
    public Map<String, Object> streamCardDemo(@PathVariable String channelCode,
                                              @RequestParam String receiveId,
                                              @RequestParam(defaultValue = "HERMES 增量卡片试发") String title) {
        AiChannel channel = channelService.getChannel(channelCode);
        if (channel == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "渠道不存在: " + channelCode);
        }
        List<String> steps = new ArrayList<>();
        String templateId = null;
        try {
            if (channel.getConfig() != null && !channel.getConfig().isBlank()) {
                templateId = ChannelConfigs.value(
                        objectMapper.readTree(channel.getConfig()), "cardTemplateId");
            }
        } catch (Exception e) {
            templateId = null;
        }
        StreamingCardSender sender = outboundService.streamCard(channel, receiveId);
        boolean opened = sender.open(new StreamCard(title, templateId, ""));
        steps.add("create: " + (opened ? "ok" : sender.lastError()));
        for (String delta : List.of("**第一段**\n正在生成…\n", "追加第二段。\n", "追加第三段。\n")) {
            if (!sender.append(delta)) {
                steps.add("append: " + sender.lastError());
                break;
            }
        }
        boolean finished = sender.finish();
        steps.add("finish: " + (finished ? "ok" : sender.lastError()));
        return Map.of(
                "ok", opened && finished && !sender.failed(),
                "channelCode", channelCode,
                "outTrackId", sender.handle() == null ? "" : sender.handle().outTrackId(),
                "steps", steps,
                "content", sender.content(),
                "error", sender.lastError() == null ? "" : sender.lastError());
    }

    @GetMapping("/api/channels/{channelCode}/users")
    public List<AiChannelUser> listPairings(@PathVariable String channelCode) {        return channelService.listPairings(channelCode);
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
