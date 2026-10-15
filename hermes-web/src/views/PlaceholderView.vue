<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? ''))
const group = computed(() => String(route.meta.group ?? ''))

/**
 * 占位屏：只作兜底（导航新增了路径但还没写屏时）。
 * /qa、/report/smart 已是绑定身份包的真实功能屏（ADR-014），不再走这里。
 */
const HINTS: Record<string, string> = {}

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
