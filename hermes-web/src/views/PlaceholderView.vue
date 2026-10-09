<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? ''))
const group = computed(() => String(route.meta.group ?? ''))

/** 占位屏的真实原因——如实标注，不糊弄（判据=后端有没有端点/权威在哪） */
const HINTS: Record<string, string> = {
  '/admin/agent/plugins': '补充项（用户指定保留）：插件机制，当前无实现。',
  '/qa': '迁移项：智能问数数据面（database.metric.query 语义层）由 Java 平台提供，本平台只做人格包 + 执行器。',
  '/report/smart': '迁移项：智能报表·千人千面基于控制塔接口（白板注「已完成-待迁移」）。',
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
