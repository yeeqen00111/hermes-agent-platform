<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? ''))
const group = computed(() => String(route.meta.group ?? ''))

/** 占位屏的真实原因——如实标注，不糊弄（判据=后端有没有端点/权威在哪） */
const HINTS: Record<string, string> = {
  '/admin/system/log-collect': '迁移项：日志采集（kafka / filebeat-json / 解析规则）与索引在 Java 平台，本平台只做适配层。',
  '/admin/system/nacos': '迁移项：nacos 服务器（凭据 / 配置分类 / 服务分类）数据面在 Java 平台，本平台只做适配层。',
  '/admin/org/users': '角色 / 菜单权威在控制塔（白板 ◆复用控制塔六项），本平台复用、不重造；人员主数据见「人员管理」。',
  '/admin/org/tenant': '租户实体与权限权威在控制塔，本平台复用；等 Java 侧提供租户接口后接入。',
  '/admin/org/platform-config': '平台日志 / 大模型配置 / 用量分析 / 数据看板——后端暂无独立管理端点（用量可从「工具调用审计」取数）。',
  '/admin/org/iterations': '迭代管理·灰度：后端 🟡 待补（无 Controller）。',
  '/admin/agent/commands': '指令（ai_command_bundle）经对话指令（/model、/bundle）生效，当前无 REST 管理端点。',
  '/admin/agent/models': '模型（ai_model / model_provider）经对话指令（/model）生效，当前无 REST 管理端点。',
  '/report/half-day': '半天报表-AI 已接入「看板监控」页（右上「生成半天报表-AI」按钮，POST /api/dashboard/half-day-report）；定时发送由平台 cron 09:00/15:00 负责。',
  '/ops/log-alert-rules': '迁移项：智能运维（日志告警规则 / 告警监控 / 告警发送日志 / 业务指标监控 /【AI】告警报表 / 授权 / 日志清理）——现成系统在岚图，本平台只做适配。',
  '/ops/alert-monitor': '迁移项：告警监控在岚图现成系统，本平台只做适配。',
  '/ops/alert-send-log': '迁移项：告警发送日志在岚图现成系统，本平台只做适配。',
  '/ops/metric-monitor': '迁移项：业务指标监控在岚图现成系统，本平台只做适配。',
  '/ops/alert-report': '迁移项：【AI】智能告警报表在岚图现成系统，本平台只做适配。',
  '/ops/authorize': '迁移项：【AI】智能运维授权在岚图现成系统，本平台只做适配。',
  '/ops/log-clean': '迁移项：日志清理（3/7/7 天）在岚图现成系统，本平台只做适配。',
  '/qa': '迁移项：智能问数数据面（database.metric.query 语义层）由 Java 平台提供，本平台只做人格包 + 执行器。',
  '/report/smart': '迁移项：智能报表·千人千面数据面由 Java 平台提供，本平台只做人格包 + 执行器。',
  '/knowledge': '迁移项：运维知识库（目录层级 / markdown）存储与检索由 Java 平台提供，本平台只做适配。',
}

const hint = computed(() => HINTS[route.path] ?? '本模块按白板逐屏实现中。')
</script>

<template>
  <el-card class="placeholder" shadow="never">
    <template #header>
      <b>{{ group }}</b>
      <span class="sep">/</span>
      <span>{{ title }}</span>
    </template>
    <el-empty :description="`「${title}」界面待实现`">
      <p class="note">{{ hint }}</p>
      <p class="note muted">接口清单见仓库根《接口契约》§3；白板覆盖率见 `00-status.md` §7。</p>
    </el-empty>
  </el-card>
</template>

<style scoped>
.placeholder {
  height: 100%;
}
.sep {
  color: #c0c4cc;
  margin: 0 6px;
}
.note {
  color: #606266;
  font-size: 13px;
  max-width: 640px;
  line-height: 1.7;
}
.muted {
  color: #909399;
}
</style>
