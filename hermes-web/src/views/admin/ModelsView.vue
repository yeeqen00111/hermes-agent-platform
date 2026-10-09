<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface AiModel {
  id: number
  providerCode: string
  modelName: string
  displayName?: string
  tier?: string
  contextWindow?: number
  supportsTools?: number
  enabled?: number
}

interface ModelProvider {
  id: number
  providerCode: string
  name: string
  baseUrl?: string
  apiKeyRef?: string
  enabled?: number
  updateTime?: string
}

const tab = ref('models')
const loading = ref(false)
const models = ref<AiModel[]>([])
const providers = ref<ModelProvider[]>([])

const filters = reactive({ providerCode: '', toolsOnly: false, includeDisabled: false })

async function load() {
  loading.value = true
  try {
    const params: Record<string, string | boolean> = {
      toolsOnly: filters.toolsOnly,
      includeDisabled: filters.includeDisabled,
    }
    if (filters.providerCode) params.providerCode = filters.providerCode
    const [m, p] = await Promise.all([
      http.get<AiModel[]>('/models', { params }),
      http.get<ModelProvider[]>('/model-providers', { params: { includeDisabled: filters.includeDisabled } }),
    ])
    models.value = m.data ?? []
    providers.value = p.data ?? []
  } catch (e) {
    ElMessage.error('加载模型目录失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.providerCode = ''
  filters.toolsOnly = false
  filters.includeDisabled = false
}

const verifyDialog = ref(false)
const verifyResult = ref<{ ok: boolean; issues: string[]; tiers: { tier: string; providerCode: string; modelName: string; displayName?: string; contextWindow?: number }[] } | null>(null)

async function runVerify() {
  try {
    const { data } = await http.get('/models/verify')
    verifyResult.value = data
    verifyDialog.value = true
    if (data.ok) {
      ElMessage.success('型号映射核验通过')
    } else {
      ElMessage.warning(`发现 ${(data.issues ?? []).length} 个问题`)
    }
  } catch (e) {
    ElMessage.error('核验失败：' + (e as Error).message)
  }
}

watch(filters, load)

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>Agent · 模型</b>
          <span class="muted">契约 §3.5 <code>GET /api/models</code>（只读）；密钥只存引用（<code>api_key_ref</code> 为环境变量名），库中无明文</span>
          <el-button size="small" @click="runVerify">型号映射核验</el-button>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>

      <el-form :inline="true" class="filters">
        <el-form-item label="供应商"><el-input v-model="filters.providerCode" size="small" style="width: 160px" clearable placeholder="如 deepseek" /></el-form-item>
        <el-form-item label="仅支持工具调用"><el-switch v-model="filters.toolsOnly" size="small" /></el-form-item>
        <el-form-item label="含停用"><el-switch v-model="filters.includeDisabled" size="small" /></el-form-item>
        <el-form-item>
          <el-button size="small" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-tabs v-model="tab">
        <el-tab-pane :label="`模型（${models.length}）`" name="models">
          <el-table v-loading="loading" :data="models" border stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="providerCode" label="供应商" width="140" />
            <el-table-column prop="modelName" label="模型" min-width="200" />
            <el-table-column prop="displayName" label="展示名" width="150" />
            <el-table-column prop="tier" label="档位" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.tier === 'PRO' ? 'danger' : row.tier === 'FLASH' ? 'warning' : 'info'">{{ row.tier ?? '—' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="contextWindow" label="上下文窗口" width="120" />
            <el-table-column label="工具调用" width="100">
              <template #default="{ row }">
                <el-tag :type="row.supportsTools ? 'success' : 'info'" size="small">{{ row.supportsTools ? '支持' : '不支持' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`供应商（${providers.length}）`" name="providers">
          <el-table v-loading="loading" :data="providers" border stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="providerCode" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="baseUrl" label="Base URL" min-width="240" />
            <el-table-column prop="apiKeyRef" label="密钥引用（环境变量名）" min-width="220" />
            <el-table-column label="启用" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" width="170" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="verifyDialog" title="大模型型号映射核验" width="720px">
      <template v-if="verifyResult">
        <p>
          <el-tag :type="verifyResult.ok ? 'success' : 'danger'" size="small">{{ verifyResult.ok ? '通过' : '有问题' }}</el-tag>
          <span class="muted">（DeepSeek Pro / DeepSeek Flash / GLM 档位映射与 Agent 引用一致性）</span>
        </p>
        <el-table :data="verifyResult.tiers" border stripe size="small" style="margin-bottom: 10px">
          <el-table-column prop="tier" label="档位" width="110" />
          <el-table-column prop="displayName" label="展示名" width="150" />
          <el-table-column prop="providerCode" label="供应商" width="120" />
          <el-table-column prop="modelName" label="模型" min-width="180" />
          <el-table-column prop="contextWindow" label="窗口" width="100" />
        </el-table>
        <el-alert v-if="verifyResult.issues.length" type="warning" :closable="false" title="核验问题">
          <ul class="issues">
            <li v-for="(i, idx) in verifyResult.issues" :key="idx">{{ i }}</li>
          </ul>
        </el-alert>
      </template>
      <template #footer>
        <el-button @click="verifyDialog = false">关闭</el-button>
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
.head .el-button {
  margin-left: auto;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.filters {
  flex-wrap: wrap;
}
</style>
