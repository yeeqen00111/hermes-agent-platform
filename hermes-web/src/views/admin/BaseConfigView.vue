<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface DbConnection {
  id?: number
  code: string
  name: string
  dbType: string
  host?: string
  port?: number
  databaseName?: string
  username?: string
  password?: string
  status?: string
}

interface NotifyChannel {
  id?: number
  code: string
  name: string
  channelType: string
  config?: string
  enabled: number
}

interface NotifyTemplate {
  id?: number
  code: string
  name: string
  channelType: string
  titleTemplate?: string
  contentTemplate?: string
  enabled: number
}

interface NotifyLog {
  id: number
  channelCode?: string
  channelType?: string
  recipient?: string
  title?: string
  status?: string
  error?: string
  createTime?: string
}

const DB_TYPES = ['MYSQL', 'OCEANBASE', 'SQLITE']
const CHANNEL_TYPES = ['FEISHU', 'EMAIL', 'DINGTALK', 'WECOM']

const tab = ref('db')
const loading = ref(false)

const dbs = ref<DbConnection[]>([])
const channels = ref<NotifyChannel[]>([])
const templates = ref<NotifyTemplate[]>([])
const logs = ref<NotifyLog[]>([])

const dbVisible = ref(false)
const dbForm = reactive<DbConnection>({ code: '', name: '', dbType: 'MYSQL', port: 3306 })
const chVisible = ref(false)
const chForm = reactive<NotifyChannel>({ code: '', name: '', channelType: 'FEISHU', enabled: 1, config: '' })
const tplVisible = ref(false)
const tplForm = reactive<NotifyTemplate>({ code: '', name: '', channelType: 'FEISHU', enabled: 1 })

const testForm = reactive<{ channelCode: string; recipient: string }>({ channelCode: '', recipient: '' })

async function loadAll() {
  loading.value = true
  try {
    const [d, c, t, l] = await Promise.all([
      http.get<DbConnection[]>('/admin/db-connections'),
      http.get<NotifyChannel[]>('/admin/notify/channels'),
      http.get<NotifyTemplate[]>('/admin/notify/templates'),
      http.get<NotifyLog[]>('/admin/notify/logs', { params: { limit: 50 } }),
    ])
    dbs.value = d.data ?? []
    channels.value = c.data ?? []
    templates.value = t.data ?? []
    logs.value = l.data ?? []
  } catch (e) {
    ElMessage.error('加载基础服务配置失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openDb(row?: DbConnection) {
  if (row) Object.assign(dbForm, row, { password: '' })
  else Object.assign(dbForm, { id: undefined, code: '', name: '', dbType: 'MYSQL', host: '', port: 3306, databaseName: '', username: '', password: '', status: 'ACTIVE' })
  dbVisible.value = true
}
async function saveDb() {
  if (!dbForm.code || !dbForm.name) {
    ElMessage.warning('code 与 name 必填')
    return
  }
  await http.post('/admin/db-connections', { ...dbForm })
  ElMessage.success('已保存')
  dbVisible.value = false
  await loadAll()
}
async function testDb(row: DbConnection) {
  if (!row.id) return
  const { data } = await http.post<{ success: boolean; message?: string }>(`/admin/db-connections/${row.id}/test`)
  if (data?.success) ElMessage.success(data.message ?? '连接正常')
  else ElMessage.error(data?.message ?? '连接失败')
}

function openCh(row?: NotifyChannel) {
  if (row) Object.assign(chForm, row)
  else Object.assign(chForm, { id: undefined, code: '', name: '', channelType: 'FEISHU', enabled: 1, config: '' })
  chVisible.value = true
}
async function saveCh() {
  if (!chForm.code || !chForm.name) {
    ElMessage.warning('code 与 name 必填')
    return
  }
  await http.post('/admin/notify/channels', { ...chForm })
  ElMessage.success('已保存')
  chVisible.value = false
  await loadAll()
}
async function removeCh(row: NotifyChannel) {
  if (!row.id) return
  await ElMessageBox.confirm(`删除通道 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/notify/channels/${row.id}`)
  await loadAll()
}

function openTpl(row?: NotifyTemplate) {
  if (row) Object.assign(tplForm, row)
  else Object.assign(tplForm, { id: undefined, code: '', name: '', channelType: 'FEISHU', titleTemplate: '', contentTemplate: '', enabled: 1 })
  tplVisible.value = true
}
async function saveTpl() {
  if (!tplForm.code || !tplForm.name) {
    ElMessage.warning('code 与 name 必填')
    return
  }
  await http.post('/admin/notify/templates', { ...tplForm })
  ElMessage.success('已保存')
  tplVisible.value = false
  await loadAll()
}
async function removeTpl(row: NotifyTemplate) {
  if (!row.id) return
  await ElMessageBox.confirm(`删除模板 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/notify/templates/${row.id}`)
  await loadAll()
}

async function testSend() {
  if (!testForm.channelCode) {
    ElMessage.warning('请填通道编码')
    return
  }
  const { data } = await http.post<{ success: boolean }>('/admin/notify/test-send', null, {
    params: { channelCode: testForm.channelCode, recipient: testForm.recipient },
  })
  if (data?.success) ElMessage.success('已发送（见发送日志）')
  else ElMessage.error('发送失败（见发送日志）')
  await loadAll()
}

onMounted(loadAll)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header><b>系统层 · 基础服务层配置</b></template>
      <el-tabs v-model="tab">
        <el-tab-pane label="数据库连接" name="db">
          <div class="bar"><el-button type="primary" size="small" @click="openDb()">新建连接</el-button></div>
          <el-table v-loading="loading" :data="dbs" border stripe size="small">
            <el-table-column prop="code" label="编码" width="140" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="dbType" label="类型" width="110" />
            <el-table-column prop="host" label="主机" min-width="150" />
            <el-table-column prop="port" label="端口" width="80" />
            <el-table-column prop="databaseName" label="库名" min-width="120" />
            <el-table-column prop="username" label="用户名" width="120" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDb(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="testDb(row)">测试</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="告警通道" name="channel">
          <div class="bar">
            <el-button type="primary" size="small" @click="openCh()">新建通道</el-button>
            <el-input v-model="testForm.channelCode" size="small" style="width: 150px" placeholder="通道编码" />
            <el-input v-model="testForm.recipient" size="small" style="width: 180px" placeholder="接收人（可选）" />
            <el-button size="small" @click="testSend">联调发送</el-button>
          </div>
          <el-table v-loading="loading" :data="channels" border stripe size="small">
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column prop="channelType" label="类型" width="110" />
            <el-table-column prop="config" label="配置" min-width="220" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openCh(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeCh(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="告警模板" name="template">
          <div class="bar"><el-button type="primary" size="small" @click="openTpl()">新建模板</el-button></div>
          <el-table v-loading="loading" :data="templates" border stripe size="small">
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="channelType" label="类型" width="110" />
            <el-table-column prop="titleTemplate" label="标题模板" min-width="200" />
            <el-table-column prop="contentTemplate" label="内容模板" min-width="240" />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openTpl(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeTpl(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="发送日志" name="log">
          <el-table v-loading="loading" :data="logs" border stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="channelCode" label="通道" width="130" />
            <el-table-column prop="channelType" label="类型" width="100" />
            <el-table-column prop="recipient" label="接收人" min-width="130" />
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="error" label="错误" min-width="180" />
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dbVisible" :title="dbForm.id ? '编辑连接' : '新建连接'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="dbForm.code" :disabled="!!dbForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="dbForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="dbForm.dbType" style="width: 100%">
            <el-option v-for="t in DB_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机"><el-input v-model="dbForm.host" placeholder="SQLITE 时填文件路径" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="dbForm.port" :min="0" /></el-form-item>
        <el-form-item label="库名"><el-input v-model="dbForm.databaseName" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="dbForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="dbForm.password" type="password" placeholder="编辑时留空则不修改" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dbVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDb">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chVisible" :title="chForm.id ? '编辑通道' : '新建通道'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="chForm.code" :disabled="!!chForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="chForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="chForm.channelType" style="width: 100%">
            <el-option v-for="t in CHANNEL_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置 JSON"><el-input v-model="chForm.config" type="textarea" :rows="3" placeholder='如 {"webhook":"https://..."}' /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="chForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCh">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tplVisible" :title="tplForm.id ? '编辑模板' : '新建模板'" width="600px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="tplForm.code" :disabled="!!tplForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="tplForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="tplForm.channelType" style="width: 100%">
            <el-option v-for="t in CHANNEL_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题模板"><el-input v-model="tplForm.titleTemplate" placeholder="{{title}}" /></el-form-item>
        <el-form-item label="内容模板"><el-input v-model="tplForm.contentTemplate" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="tplForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tplVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTpl">保存</el-button>
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
.bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
</style>
