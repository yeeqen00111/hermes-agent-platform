<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface SysTenant {
  id?: number
  code: string
  name: string
  contact?: string
  phone?: string
  status?: string
  remark?: string
}

const loading = ref(false)
const tenants = ref<SysTenant[]>([])
const dialog = ref(false)
const saving = ref(false)
const form = reactive<SysTenant>({ code: '', name: '', status: 'ACTIVE' })

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<SysTenant[]>('/admin/tenants')
    tenants.value = data ?? []
  } catch (e) {
    ElMessage.error('加载租户失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, code: '', name: '', contact: '', phone: '', status: 'ACTIVE', remark: '' })
  dialog.value = true
}

function openEdit(row: SysTenant) {
  Object.assign(form, row)
  dialog.value = true
}

async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  saving.value = true
  try {
    await http.post('/admin/tenants', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: SysTenant) {
  await ElMessageBox.confirm(`删除租户 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/tenants/${row.id}`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>平台层 · 系统层 · 租户管理</b>
          <span class="muted">租户实体维护（多租户隔离的归属）</span>
          <el-button type="primary" size="small" @click="openCreate">新建租户</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tenants" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="code" label="编码" width="160" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="contact" label="联系人" width="140" />
        <el-table-column prop="phone" label="联系电话" width="150" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑租户' : '新建租户'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
</style>
