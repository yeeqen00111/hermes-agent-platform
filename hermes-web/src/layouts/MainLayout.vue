<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { navGroups } from '@/nav'
import { getActorUserId, setActorUserId } from '@/api/actor'

const route = useRoute()
const active = computed(() => route.path)
const pageTitle = computed(() => String(route.meta.title ?? '智能开发运维平台'))

const actorId = ref(getActorUserId())
function saveActor() {
  setActorUserId(actorId.value)
  ElMessage.success('已切换操作人 UID=' + actorId.value)
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="272px" class="aside">
      <div class="brand">
        <div class="brand-title">HERMES</div>
        <div class="brand-sub">智能开发运维平台</div>
      </div>
      <el-scrollbar>
        <el-menu :default-active="active" router class="menu" background-color="transparent" text-color="#c7d0db" active-text-color="#ffffff">
          <el-sub-menu v-for="group in navGroups" :key="group.title" :index="group.title">
            <template #title>{{ group.title }}</template>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              {{ item.title }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="header-title">{{ pageTitle }}</span>
        <span class="header-right">
          <span class="hint">操作人</span>
          <el-input
            v-model.number="actorId"
            size="small"
            style="width: 96px"
            @keyup.enter="saveActor"
            @blur="saveActor"
          />
        </span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: var(--hermes-aside-bg);
  display: flex;
  flex-direction: column;
}
.brand {
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.brand-title {
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}
.brand-sub {
  color: #8b98a8;
  font-size: 12px;
  margin-top: 2px;
}
.menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 12px;
}
.hint {
  color: #909399;
  font-size: 12px;
}
.main {
  padding: 16px;
}
</style>
