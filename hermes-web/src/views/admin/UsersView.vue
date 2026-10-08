<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface SysUser {
  id?: number
  userCode: string
  name: string
  email?: string
  feishu?: string
  phone?: string
  roleCodes?: string
  isAdmin?: number
  status?: string
}

const users = ref<SysUser[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<SysUser>({ userCode: '', name: '', status: 'ACTIVE', isAdmin: 0 })

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<SysUser[]>('/admin/users')
    users.value = data ?? []
  } catch (e) {
    ElMessage.error('加载人员失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, userCode: '', name: '', email: '', feishu: '', phone: '', roleCodes: '', isAdmin: 0, status: 'ACTIVE' })
  dialogVisible.value = true
}

function openEdit(row: SysUser) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.userCode || !form.name) {
    ElMessage.warning('userCode 与 name 必填')
    return
  }
  saving.value = true
  try {
    await http.post('/admin/users', { ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: SysUser) {
  if (!row.id) return
  await ElMessageBox.confirm(`删除人员 ${row.name}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/users/${row.id}`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>系统层 · 人员管理</b>
          <span class="muted">人员主数据（角色/菜单权威在控制塔，见白板 ◆复用控制塔）</span>
          <el-button type="primary" size="small" @click="openCreate">新建人员</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="users" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userCode" label="工号" width="140" />
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="feishu" label="飞书" min-width="150" />
        <el-table-column prop="roleCodes" label="角色" min-width="140" />
        <el-table-column label="管理员" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isAdmin ? 'warning' : 'info'" size="small">{{ row.isAdmin ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑人员' : '新建人员'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="工号" required><el-input v-model="form.userCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="飞书"><el-input v-model="form.feishu" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="角色编码"><el-input v-model="form.roleCodes" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="管理员"><el-switch v-model="form.isAdmin" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.view {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.head .el-button {
  margin-left: auto;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
