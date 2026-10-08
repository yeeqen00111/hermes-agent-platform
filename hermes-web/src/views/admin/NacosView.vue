<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface NacosServer {
  id?: number
  code: string
  name: string
  serverAddr?: string
  namespaceId?: string
  username?: string
  secretRef?: string
  enabled: number
  remark?: string
}

interface NacosCategory {
  id?: number
  code: string
  name: string
  categoryType: string
  serverCode?: string
  systemName?: string
  groupName?: string
  matchPattern?: string
  enabled: number
  remark?: string
}

const tab = ref('server')
const loading = ref(false)

const servers = ref<NacosServer[]>([])
const categories = ref<NacosCategory[]>([])

const serverDialog = ref(false)
const serverSaving = ref(false)
const serverForm = reactive<NacosServer>({
  code: '',
  name: '',
  serverAddr: 'http://localhost:8848',
  namespaceId: 'public',
  username: '',
  secretRef: '',
  enabled: 1,
  remark: '',
})

const categoryDialog = ref(false)
const categorySaving = ref(false)
const emptyCategory = (type: string): NacosCategory => ({
  code: '',
  name: '',
  categoryType: type,
  serverCode: '',
  systemName: '',
  groupName: 'DEFAULT_GROUP',
  matchPattern: '',
  enabled: 1,
  remark: '',
})
const categoryForm = reactive<NacosCategory>(emptyCategory('CONFIG'))

/** 当前 Tab 对应的分类类型 */
const activeCategoryType = computed(() => (tab.value === 'service' ? 'SERVICE' : 'CONFIG'))
const visibleCategories = computed(() =>
  categories.value.filter((c) => c.categoryType === activeCategoryType.value),
)

async function loadServers() {
  const { data } = await http.get<NacosServer[]>('/admin/nacos/servers')
  servers.value = data ?? []
}

async function loadCategories() {
  const { data } = await http.get<NacosCategory[]>('/admin/nacos/categories')
  categories.value = data ?? []
}

async function load() {
  loading.value = true
  try {
    await Promise.all([loadServers(), loadCategories()])
  } catch (e) {
    ElMessage.error('加载 nacos 配置失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

// ---------- 配置凭据 ----------

function openCreateServer() {
  Object.assign(serverForm, {
    id: undefined,
    code: '',
    name: '',
    serverAddr: 'http://localhost:8848',
    namespaceId: 'public',
    username: '',
    secretRef: '',
    enabled: 1,
    remark: '',
  })
  serverDialog.value = true
}

function openEditServer(row: NacosServer) {
  Object.assign(serverForm, row)
  serverDialog.value = true
}

async function saveServer() {
  if (!serverForm.code || !serverForm.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  serverSaving.value = true
  try {
    await http.post('/admin/nacos/servers', { ...serverForm })
    ElMessage.success('已保存')
    serverDialog.value = false
    await loadServers()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    serverSaving.value = false
  }
}

async function removeServer(row: NacosServer) {
  await ElMessageBox.confirm(`删除 nacos 服务器 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/nacos/servers/${row.id}`)
  await loadServers()
}

async function testServer(row: NacosServer) {
  const { data } = await http.post<{ success: boolean; message?: string }>(`/admin/nacos/servers/${row.code}/test`)
  if (data.success) {
    ElMessage.success(data.message ?? '连接正常')
  } else {
    ElMessage.error(data.message ?? '连接失败')
  }
}

// ---------- 配置分类 / 服务分类 ----------

function openCreateCategory() {
  Object.assign(categoryForm, emptyCategory(activeCategoryType.value))
  categoryDialog.value = true
}

function openEditCategory(row: NacosCategory) {
  Object.assign(categoryForm, emptyCategory(row.categoryType), row)
  categoryDialog.value = true
}

async function saveCategory() {
  if (!categoryForm.code || !categoryForm.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  categorySaving.value = true
  try {
    await http.post('/admin/nacos/categories', { ...categoryForm })
    ElMessage.success('已保存')
    categoryDialog.value = false
    await loadCategories()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    categorySaving.value = false
  }
}

async function removeCategory(row: NacosCategory) {
  await ElMessageBox.confirm(`删除分类 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/nacos/categories/${row.id}`)
  await loadCategories()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>系统管理 · nacos 服务器</b>
          <span class="muted">配置凭据（只存引用，不存明文） / 配置分类 · 服务分类（与系统挂钩）</span>
        </div>
      </template>

      <el-tabs v-model="tab">
        <el-tab-pane label="配置凭据" name="server">
          <div class="toolbar">
            <span class="muted">nacos 连接信息；凭据只存环境变量名，探测走 /nacos/v1/console/server/state</span>
            <el-button type="primary" size="small" @click="openCreateServer">新建服务器</el-button>
          </div>
          <el-table v-loading="loading" :data="servers" border stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="code" label="编码" width="130" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column prop="serverAddr" label="地址" min-width="200" />
            <el-table-column prop="namespaceId" label="命名空间" width="110" />
            <el-table-column prop="username" label="账号" width="100" />
            <el-table-column prop="secretRef" label="凭据引用" width="180" show-overflow-tooltip />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditServer(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="testServer(row)">测试连接</el-button>
                <el-button link type="danger" size="small" @click="removeServer(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="配置分类" name="config">
          <div class="toolbar">
            <span class="muted">让 nacos 配置与系统挂钩（data_id 匹配 + group）</span>
            <el-button type="primary" size="small" @click="openCreateCategory">新建配置分类</el-button>
          </div>
          <el-table v-loading="loading" :data="visibleCategories" border stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column prop="serverCode" label="服务器" width="120" />
            <el-table-column prop="systemName" label="挂钩系统" min-width="140" />
            <el-table-column prop="groupName" label="group" width="130" />
            <el-table-column prop="matchPattern" label="匹配模式" min-width="150" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditCategory(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeCategory(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="服务分类" name="service">
          <div class="toolbar">
            <span class="muted">让 nacos 服务与系统挂钩（服务名前缀匹配）</span>
            <el-button type="primary" size="small" @click="openCreateCategory">新建服务分类</el-button>
          </div>
          <el-table v-loading="loading" :data="visibleCategories" border stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column prop="serverCode" label="服务器" width="120" />
            <el-table-column prop="systemName" label="挂钩系统" min-width="140" />
            <el-table-column prop="groupName" label="group" width="130" />
            <el-table-column prop="matchPattern" label="匹配模式" min-width="150" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditCategory(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeCategory(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="serverDialog" :title="serverForm.id ? '编辑 nacos 服务器' : '新建 nacos 服务器'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="serverForm.code" :disabled="!!serverForm.id" placeholder="如 nacos-prod" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="serverForm.name" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="serverForm.serverAddr" placeholder="http://host:8848" /></el-form-item>
        <el-form-item label="命名空间"><el-input v-model="serverForm.namespaceId" placeholder="public" /></el-form-item>
        <el-form-item label="账号"><el-input v-model="serverForm.username" /></el-form-item>
        <el-form-item label="凭据引用"><el-input v-model="serverForm.secretRef" placeholder="环境变量名（不存明文）" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="serverForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="serverForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serverDialog = false">取消</el-button>
        <el-button type="primary" :loading="serverSaving" @click="saveServer">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="categoryDialog"
      :title="(categoryForm.id ? '编辑' : '新建') + (categoryForm.categoryType === 'SERVICE' ? '服务分类' : '配置分类')"
      width="580px"
    >
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="categoryForm.code" :disabled="!!categoryForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="categoryForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="categoryForm.categoryType">
            <el-radio value="CONFIG">配置分类</el-radio>
            <el-radio value="SERVICE">服务分类</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="nacos 服务器">
          <el-select v-model="categoryForm.serverCode" clearable style="width: 100%">
            <el-option v-for="s in servers" :key="s.code" :label="s.code" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="挂钩系统"><el-input v-model="categoryForm.systemName" placeholder="如 订单系统" /></el-form-item>
        <el-form-item label="group"><el-input v-model="categoryForm.groupName" placeholder="DEFAULT_GROUP" /></el-form-item>
        <el-form-item label="匹配模式">
          <el-input
            v-model="categoryForm.matchPattern"
            :placeholder="categoryForm.categoryType === 'SERVICE' ? '服务名前缀，如 order-' : 'data_id 匹配，如 order-*.yaml'"
          />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="categoryForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="categoryForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="saveCategory">保存</el-button>
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
  flex-wrap: wrap;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.toolbar .el-button {
  margin-left: auto;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
