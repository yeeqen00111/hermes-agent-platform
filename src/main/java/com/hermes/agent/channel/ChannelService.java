package com.hermes.agent.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AiChannel;
import com.hermes.agent.entity.AiChannelUser;
import com.hermes.agent.mapper.AiChannelMapper;
import com.hermes.agent.mapper.AiChannelUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 渠道管理：配置 CRUD、连接状态/心跳、用户配对（契约 §5.2）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private final AiChannelMapper channelMapper;
    private final AiChannelUserMapper channelUserMapper;

    // ---------- 渠道配置 ----------

    public List<AiChannel> listChannels() {
        return channelMapper.selectList(new LambdaQueryWrapper<AiChannel>()
                .orderByDesc(AiChannel::getId));
    }

    public AiChannel getChannel(String channelCode) {
        return channelMapper.selectOne(new LambdaQueryWrapper<AiChannel>()
                .eq(AiChannel::getChannelCode, channelCode));
    }

    public AiChannel saveChannel(AiChannel channel) {
        if (channel.getChannelCode() == null || channel.getChannelCode().isBlank()
                || channel.getChannelType() == null || channel.getChannelType().isBlank()) {
            throw new IllegalArgumentException("channelCode 与 channelType 必填");
        }
        if (channel.getId() != null) {
            channelMapper.updateById(channel);
        } else {
            channelMapper.insert(channel);
        }
        return channel;
    }

    public void deleteChannel(String channelCode) {
        AiChannel channel = getChannel(channelCode);
        if (channel != null) {
            channelMapper.deleteById(channel.getId());
        }
        channelUserMapper.delete(new LambdaQueryWrapper<AiChannelUser>()
                .eq(AiChannelUser::getChannelCode, channelCode));
    }

    // ---------- 连接状态 ----------

    public void heartbeat(String channelCode, String instanceId) {
        AiChannel channel = getChannel(channelCode);
        if (channel == null) {
            throw new IllegalArgumentException("渠道不存在: " + channelCode);
        }
        channel.setOwnerInstance(instanceId);
        channel.setHeartbeatTime(LocalDateTime.now());
        channel.setStatus("CONNECTED");
        channel.setErrorMessage(null);
        channelMapper.updateById(channel);
    }

    public void markStatus(String channelCode, String status, String errorMessage) {
        AiChannel channel = getChannel(channelCode);
        if (channel == null) {
            return;
        }
        channel.setStatus(status);
        channel.setErrorMessage(errorMessage);
        channelMapper.updateById(channel);
    }

    /** 心跳超时（2 分钟）自动置为 DISCONNECTED */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void sweepStaleConnections() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        List<AiChannel> stale = channelMapper.selectList(new LambdaQueryWrapper<AiChannel>()
                .eq(AiChannel::getStatus, "CONNECTED")
                .lt(AiChannel::getHeartbeatTime, threshold));
        for (AiChannel channel : stale) {
            channel.setStatus("DISCONNECTED");
            channel.setErrorMessage("心跳超时");
            channelMapper.updateById(channel);
            log.warn("渠道连接心跳超时，置为DISCONNECTED: {}", channel.getChannelCode());
        }
    }

    // ---------- 用户配对 ----------

    public List<AiChannelUser> listPairings(String channelCode) {
        return channelUserMapper.selectList(new LambdaQueryWrapper<AiChannelUser>()
                .eq(AiChannelUser::getChannelCode, channelCode)
                .orderByAsc(AiChannelUser::getId));
    }

    public AiChannelUser pair(String channelCode, String channelUserId, Long platformUserId, String pairedBy) {
        if (platformUserId == null) {
            throw new IllegalArgumentException("platformUserId 必填");
        }
        channelUserMapper.delete(new LambdaQueryWrapper<AiChannelUser>()
                .eq(AiChannelUser::getChannelCode, channelCode)
                .eq(AiChannelUser::getChannelUserId, channelUserId));
        AiChannelUser mapping = new AiChannelUser();
        mapping.setChannelCode(channelCode);
        mapping.setChannelUserId(channelUserId);
        mapping.setPlatformUserId(platformUserId);
        mapping.setPairedTime(LocalDateTime.now());
        mapping.setPairedBy(pairedBy == null || pairedBy.isBlank() ? "ADMIN" : pairedBy);
        channelUserMapper.insert(mapping);
        return mapping;
    }

    public void unpair(String channelCode, String channelUserId) {
        channelUserMapper.delete(new LambdaQueryWrapper<AiChannelUser>()
                .eq(AiChannelUser::getChannelCode, channelCode)
                .eq(AiChannelUser::getChannelUserId, channelUserId));
    }

    /** open_id → 平台 userId；未绑定返回 empty（未绑定用户不进 Agent） */
    public Optional<Long> resolvePlatformUser(String channelCode, String channelUserId) {
        AiChannelUser mapping = channelUserMapper.selectOne(new LambdaQueryWrapper<AiChannelUser>()
                .eq(AiChannelUser::getChannelCode, channelCode)
                .eq(AiChannelUser::getChannelUserId, channelUserId));
        return mapping == null ? Optional.empty() : Optional.of(mapping.getPlatformUserId());
    }
}
