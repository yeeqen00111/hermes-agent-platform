<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? ''))
const group = computed(() => String(route.meta.group ?? ''))
</script>

<template>
  <el-card class="placeholder" shadow="never">
    <template #header>
      <b>{{ group }}</b>
      <span class="sep">/</span>
      <span>{{ title }}</span>
    </template>
    <el-empty :description="`「${title}」界面待实现`">
      <p class="note">
        白板对应区块见 <code>00-status.md</code>；后端接口清单见《接口契约》§3。
        本页为导航占位——按白板逐屏实现。
      </p>
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
  max-width: 520px;
}
</style>
