<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? ''))
const group = computed(() => String(route.meta.group ?? ''))

/** 占位屏的真实原因——如实标注，不糊弄（判据=后端有没有端点/权威在哪） */
const HINTS: Record<string, string> = {
  '/admin/system/log-collect': '本平台实现中：日志采集（采集通道 kafka/消息队列/兼容 filebeat-json + 日志 JSON 解析规则），按白板「系统管理」在本平台建设。',
  '/admin/system/nacos': '本平台实现中：nacos 服务器（配置凭据 / 配置分类 / 服务分类），按白板「系统管理」在本平台建设。',
  '/admin/org/users': '角色 / 菜单权威在控制塔（白板 ◆复用控制塔六项），本平台复用、不重造；人员主数据见「人员管理」。',
  '/admin/org/tenant': '租户实体与权限权威在控制塔，本平台复用；等 Java 侧提供租户接口后接入。',
  '/admin/org/platform-config': '平台日志 / 用量分析 / 数据看板——后端暂无独立管理端点（用量可从「工具调用审计」取数）；大模型配置见「Agent → 模型」。',
  '/admin/agent/commands': '指令（ai_command_bundle）经对话指令（/model、/bundle）生效，当前无 REST 管理端点。',
  '/admin/agent/plugins': '补充项（用户指定保留）：插件机制，当前无实现。',
  '/ops/log-alert-rules': '本平台实现中：日志告警规则（分项目一套配置 + 过滤规则：该告警/不告警/提级 + 去向），按白板「业务层·智能运维」建设。',
  '/ops/alert-monitor': '本平台实现中：告警监控（维度 按系统/服务器/日志级别、上次接收过滤时间、历史记录、触发次数）。',
  '/ops/alert-send-log': '本平台实现中：告警发送日志（告警发送历史记录）。',
  '/ops/metric-monitor': '本平台实现中：业务指标监控（埋点 / 所属系统 / 指标类型）。',
  '/ops/alert-report': '本平台实现中：【AI】智能告警报表（发送频率 / 发送内容 / 提示词 / 发送通道），按白板建设。',
  '/ops/authorize': '本平台实现中：【AI】智能运维授权（代码仓库授权 / nacos 配置授权 / 日志授权）。',
  '/ops/log-clean': '本平台实现中：日志清理策略（全量 3 天 / 告警 7 天 / 提级告警 7 天）。',
  '/qa': '迁移项：智能问数数据面（database.metric.query 语义层）由 Java 平台提供，本平台只做人格包 + 执行器。',
  '/report/smart': '迁移项：智能报表·千人千面基于控制塔接口（白板注「已完成-待迁移」）。',
  '/knowledge': '本平台实现中：运维知识库（目录层级 markdown 编辑器 / 上传转 markdown）。',
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
