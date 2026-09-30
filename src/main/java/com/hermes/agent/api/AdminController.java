package com.hermes.agent.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AiDbConnection;
import com.hermes.agent.entity.AiNotifyChannel;
import com.hermes.agent.entity.AiNotifyLog;
import com.hermes.agent.entity.AiNotifyTemplate;
import com.hermes.agent.entity.SysProject;
import com.hermes.agent.entity.SysUser;
import com.hermes.agent.mapper.AiDbConnectionMapper;
import com.hermes.agent.mapper.AiNotifyChannelMapper;
import com.hermes.agent.mapper.AiNotifyLogMapper;
import com.hermes.agent.mapper.AiNotifyTemplateMapper;
import com.hermes.agent.mapper.SysProjectMapper;
import com.hermes.agent.mapper.SysUserMapper;
import com.hermes.agent.notify.NotificationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

/**
 * 三期 平台管理面：项目 / 用户（人员）/ 库连接 / 告警通道 / 告警模板（◆ 复用控制塔六项的本地回退位）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SysProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final AiDbConnectionMapper dbConnectionMapper;
    private final AiNotifyChannelMapper notifyChannelMapper;
    private final AiNotifyTemplateMapper notifyTemplateMapper;
    private final AiNotifyLogMapper notifyLogMapper;
    private final NotificationGateway notificationGateway;

    // ---------- 项目管理 ----------

    @GetMapping("/projects")
    public List<SysProject> listProjects() {
        return projectMapper.selectList(new LambdaQueryWrapper<SysProject>()
                .orderByAsc(SysProject::getId));
    }

    @PostMapping("/projects")
    public SysProject saveProject(@RequestBody SysProject project) {
        if (project.getId() != null) {
            projectMapper.updateById(project);
        } else {
            projectMapper.insert(project);
        }
        return project;
    }

    @DeleteMapping("/projects/{id}")
    public Map<String, Object> deleteProject(@PathVariable Long id) {
        projectMapper.deleteById(id);
        return Map.of("success", true);
    }

    // ---------- 用户 / 人员管理 ----------

    @GetMapping("/users")
    public List<SysUser> listUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId));
    }

    @PostMapping("/users")
    public SysUser saveUser(@RequestBody SysUser user) {
        if (user.getId() != null) {
            userMapper.updateById(user);
        } else {
            userMapper.insert(user);
        }
        return user;
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        userMapper.deleteById(id);
        return Map.of("success", true);
    }

    // ---------- 数据库连接（◆ 库连接） ----------

    @GetMapping("/db-connections")
    public List<AiDbConnection> listDbConnections() {
        List<AiDbConnection> list = dbConnectionMapper.selectList(new LambdaQueryWrapper<AiDbConnection>()
                .orderByAsc(AiDbConnection::getId));
        list.forEach(c -> c.setPassword(null));
        return list;
    }

    @PostMapping("/db-connections")
    public AiDbConnection saveDbConnection(@RequestBody AiDbConnection connection) {
        if (connection.getId() != null) {
            if (connection.getPassword() == null) {
                AiDbConnection old = dbConnectionMapper.selectById(connection.getId());
                connection.setPassword(old == null ? null : old.getPassword());
            }
            dbConnectionMapper.updateById(connection);
        } else {
            dbConnectionMapper.insert(connection);
        }
        connection.setPassword(null);
        return connection;
    }

    @PostMapping("/db-connections/{id}/test")
    public Map<String, Object> testDbConnection(@PathVariable Long id) {
        AiDbConnection c = dbConnectionMapper.selectById(id);
        if (c == null) {
            return Map.of("success", false, "message", "连接不存在: " + id);
        }
        String url = jdbcUrl(c);
        try (Connection conn = DriverManager.getConnection(url, c.getUsername(), c.getPassword())) {
            boolean ok = conn.isValid(5);
            return Map.of("success", ok, "message", ok ? "连接正常" : "连接校验失败");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    private String jdbcUrl(AiDbConnection c) {
        String type = c.getDbType() == null ? "MYSQL" : c.getDbType().toUpperCase();
        return switch (type) {
            case "SQLITE" -> "jdbc:sqlite:" + c.getHost();
            case "OCEANBASE", "MYSQL" -> "jdbc:mysql://" + c.getHost() + ":" + c.getPort()
                    + "/" + (c.getDatabaseName() == null ? "" : c.getDatabaseName())
                    + "?connectTimeout=3000&useSSL=false&allowPublicKeyRetrieval=true";
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + c.getDbType());
        };
    }

    // ---------- 告警通道（◆ 飞书/邮件） ----------

    @GetMapping("/notify/channels")
    public List<AiNotifyChannel> listNotifyChannels() {
        return notifyChannelMapper.selectList(new LambdaQueryWrapper<AiNotifyChannel>()
                .orderByAsc(AiNotifyChannel::getId));
    }

    @PostMapping("/notify/channels")
    public AiNotifyChannel saveNotifyChannel(@RequestBody AiNotifyChannel channel) {
        if (channel.getId() != null) {
            notifyChannelMapper.updateById(channel);
        } else {
            notifyChannelMapper.insert(channel);
        }
        return channel;
    }

    @DeleteMapping("/notify/channels/{id}")
    public Map<String, Object> deleteNotifyChannel(@PathVariable Long id) {
        notifyChannelMapper.deleteById(id);
        return Map.of("success", true);
    }

    // ---------- 告警模板（◆ 告警模板） ----------

    @GetMapping("/notify/templates")
    public List<AiNotifyTemplate> listNotifyTemplates() {
        return notifyTemplateMapper.selectList(new LambdaQueryWrapper<AiNotifyTemplate>()
                .orderByAsc(AiNotifyTemplate::getId));
    }

    @PostMapping("/notify/templates")
    public AiNotifyTemplate saveNotifyTemplate(@RequestBody AiNotifyTemplate template) {
        if (template.getId() != null) {
            notifyTemplateMapper.updateById(template);
        } else {
            notifyTemplateMapper.insert(template);
        }
        return template;
    }

    @DeleteMapping("/notify/templates/{id}")
    public Map<String, Object> deleteNotifyTemplate(@PathVariable Long id) {
        notifyTemplateMapper.deleteById(id);
        return Map.of("success", true);
    }

    // ---------- 发送日志（只读）与联调发送 ----------

    @GetMapping("/notify/logs")
    public List<AiNotifyLog> listNotifyLogs(@RequestParam(required = false, defaultValue = "50") int limit) {
        return notifyLogMapper.selectList(new LambdaQueryWrapper<AiNotifyLog>()
                .orderByDesc(AiNotifyLog::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    @PostMapping("/notify/test-send")
    public Map<String, Object> testSend(@RequestParam String channelCode,
                                        @RequestParam(required = false) String recipient,
                                        @RequestParam(required = false, defaultValue = "HERMES通知联调") String title,
                                        @RequestParam(required = false, defaultValue = "这是一条联调消息") String content) {
        boolean ok = notificationGateway.sendByCode(channelCode,
                recipient == null ? "" : recipient, title, content);
        return Map.of("success", ok);
    }
}
