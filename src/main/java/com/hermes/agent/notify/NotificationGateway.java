package com.hermes.agent.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiNotifyChannel;
import com.hermes.agent.entity.AiNotifyLog;
import com.hermes.agent.mapper.AiNotifyChannelMapper;
import com.hermes.agent.mapper.AiNotifyLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Properties;

/**
 * 通知网关：飞书 webhook（HTTP，支持加签）/ 邮件 SMTP。
 * 权威通道在 ◆ 供应链控制塔，本类为平台侧集成位 + 无控制塔时的本地回退。
 * 每次发送写一行 ai_notify_log（只写），通道未配置记 NO_CHANNEL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationGateway {

    private static final int FEISHU_TEXT_LIMIT = 15000;

    private final AiNotifyChannelMapper channelMapper;
    private final AiNotifyLogMapper logMapper;
    private final ObjectMapper objectMapper;

    /** 按类型发送：取该类型第一个启用通道，接收人为邮箱地址或飞书账号 */
    public void sendByType(String channelType, String recipient, String title, String content) {
        AiNotifyChannel channel = channelMapper.selectList(new LambdaQueryWrapper<AiNotifyChannel>()
                        .eq(AiNotifyChannel::getChannelType, channelType)
                        .eq(AiNotifyChannel::getEnabled, 1))
                .stream().findFirst().orElse(null);
        if (channel == null) {
            writeLog(null, channelType, recipient, title, content, "NO_CHANNEL", "无启用通道: " + channelType);
            return;
        }
        send(channel, recipient, title, content);
    }

    /** 按通道编码发送（联调用） */
    public boolean sendByCode(String channelCode, String recipient, String title, String content) {
        AiNotifyChannel channel = channelMapper.selectOne(new LambdaQueryWrapper<AiNotifyChannel>()
                .eq(AiNotifyChannel::getCode, channelCode)
                .eq(AiNotifyChannel::getEnabled, 1));
        if (channel == null) {
            writeLog(channelCode, null, recipient, title, content, "NO_CHANNEL", "通道不存在或未启用: " + channelCode);
            return false;
        }
        return send(channel, recipient, title, content);
    }

    private boolean send(AiNotifyChannel channel, String recipient, String title, String content) {
        try {
            if ("FEISHU".equalsIgnoreCase(channel.getChannelType())) {
                sendFeishu(channel, title, content);
            } else if ("EMAIL".equalsIgnoreCase(channel.getChannelType())) {
                sendEmail(channel, recipient, title, content);
            } else {
                writeLog(channel.getCode(), channel.getChannelType(), recipient, title, content,
                        "FAILED", "未知通道类型: " + channel.getChannelType());
                return false;
            }
            writeLog(channel.getCode(), channel.getChannelType(), recipient, title, content, "SUCCESS", null);
            return true;
        } catch (Exception e) {
            log.error("通知发送失败: channel={}, recipient={}", channel.getCode(), recipient, e);
            writeLog(channel.getCode(), channel.getChannelType(), recipient, title, content, "FAILED", e.getMessage());
            return false;
        }
    }

    private void sendFeishu(AiNotifyChannel channel, String title, String content) throws Exception {
        JsonNode cfg = objectMapper.readTree(channel.getConfig());
        String webhookUrl = cfg.path("webhookUrl").asText("");
        if (webhookUrl.isBlank()) {
            throw new IllegalArgumentException("飞书通道缺少 webhookUrl");
        }
        String text = title + "\n" + content;
        if (text.length() > FEISHU_TEXT_LIMIT) {
            text = text.substring(0, FEISHU_TEXT_LIMIT) + "\n...(截断)";
        }
        var body = objectMapper.createObjectNode();
        String secret = cfg.path("secret").asText("");
        if (!secret.isBlank()) {
            long timestamp = System.currentTimeMillis() / 1000;
            body.put("timestamp", timestamp);
            body.put("sign", feishuSign(timestamp, secret));
        }
        body.put("msg_type", "text");
        body.putObject("content").put("text", text);

        RestClient restClient = RestClient.builder()
                .requestFactory(timeoutFactory())
                .build();
        String resp = restClient.post().uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve().body(String.class);
        if (resp != null && objectMapper.readTree(resp).path("code").asInt(-1) == 0) {
            return;
        }
        log.warn("飞书webhook响应: {}", resp);
    }

    private void sendEmail(AiNotifyChannel channel, String recipient, String title, String content) {
        JsonNode cfg = parseConfig(channel.getConfig());
        if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
            throw new IllegalArgumentException("邮件接收人无效: " + recipient);
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.path("smtpHost").asText(""));
        sender.setPort(cfg.path("smtpPort").asInt(25));
        String username = cfg.path("username").asText("");
        sender.setUsername(username.isBlank() ? null : username);
        sender.setPassword(cfg.path("password").asText(""));
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "5000");
        if (cfg.path("ssl").asBoolean(false)) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        String from = cfg.path("from").asText(username);
        message.setFrom(from.isBlank() ? "hermes@localhost" : from);
        message.setTo(recipient);
        message.setSubject(title);
        message.setText(content);
        sender.send(message);
    }

    private JsonNode parseConfig(String config) {
        try {
            return objectMapper.readTree(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("通道配置JSON解析失败", e);
        }
    }

    private SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return factory;
    }

    private String feishuSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal());
    }

    private void writeLog(String channelCode, String channelType, String recipient,
                          String title, String content, String status, String error) {
        AiNotifyLog entry = new AiNotifyLog();
        entry.setChannelCode(channelCode);
        entry.setChannelType(channelType);
        entry.setRecipient(recipient);
        entry.setTitle(title);
        entry.setContent(content == null ? null : (content.length() > 4000 ? content.substring(0, 4000) : content));
        entry.setStatus(status);
        entry.setError(error);
        entry.setCreateTime(LocalDateTime.now());
        logMapper.insert(entry);
    }
}
