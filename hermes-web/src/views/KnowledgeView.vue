<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface KbNode {
  id: number
  code: string
  name: string
  parentId: number
  nodeType: string
  title?: string
  children?: KbNode[]
}

const loading = ref(false)
const tree = ref<KbNode[]>([])
const selected = ref<KbNode | null>(null)

const editor = reactive({ id: 0, name: '', title: '', content: '' })
const saving = ref(false)
const preview = ref(false)

const keyword = ref('')
const searchResults = ref<{ id: number; name: string; title?: string; snippet: string }[]>([])

const fileInput = ref<HTMLInputElement | null>(null)

const isDoc = computed(() => selected.value?.nodeType === 'DOC')
const parentIdForCreate = computed(() => {
  if (!selected.value) return 0
  return selected.value.nodeType === 'FOLDER' ? selected.value.id : selected.value.parentId
})

async function loadTree() {
  loading.value = true
  try {
    const { data } = await http.get<KbNode[]>('/knowledge/tree')
    tree.value = data ?? []
  } catch (e) {
    ElMessage.error('加载知识库失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function onNodeClick(node: KbNode) {
  selected.value = node
  if (node.nodeType !== 'DOC') {
    Object.assign(editor, { id: 0, name: '', title: '', content: '' })
    return
  }
  const { data } = await http.get<KbNode & { content?: string }>(`/knowledge/nodes/${node.id}`)
  Object.assign(editor, {
    id: data.id,
    name: data.name,
    title: data.title ?? data.name,
    content: (data as { content?: string }).content ?? '',
  })
}

async function createNode(type: 'FOLDER' | 'DOC') {
  const label = type === 'FOLDER' ? '目录' : '文档'
  const { value } = await ElMessageBox.prompt(`新建${label}名称`, `新建${label}`, { inputValue: '' })
  const name = (value ?? '').trim()
  if (!name) return
  const body: Record<string, unknown> = { name, title: name, nodeType: type, parentId: parentIdForCreate.value }
  if (type === 'DOC') body.content = `# ${name}\n\n`
  try {
    const { data } = await http.post<KbNode>('/knowledge/nodes', body)
    ElMessage.success(`已新建${label}`)
    await loadTree()
    if (type === 'DOC') await onNodeClick(data)
  } catch (e) {
    ElMessage.error('新建失败：' + (e as Error).message)
  }
}

async function saveDoc() {
  if (!editor.id) return
  saving.value = true
  try {
    await http.post('/knowledge/nodes', {
      id: editor.id,
      name: editor.name,
      title: editor.title,
      content: editor.content,
      nodeType: 'DOC',
      parentId: selected.value?.parentId ?? 0,
    })
    ElMessage.success('已保存')
    await loadTree()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function removeNode() {
  if (!selected.value) return
  await ElMessageBox.confirm(`删除「${selected.value.name}」及其子节点？`, '提示', { type: 'warning' })
  await http.delete(`/knowledge/nodes/${selected.value.id}`)
  selected.value = null
  Object.assign(editor, { id: 0, name: '', title: '', content: '' })
  await loadTree()
}

function pickFile() {
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  try {
    const { data } = await http.post<KbNode>('/knowledge/upload', fd, {
      params: { parentId: parentIdForCreate.value },
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    ElMessage.success(`已上传并转为 markdown：${data.name}`)
    await loadTree()
  } catch (err) {
    ElMessage.error('上传失败：' + (err as Error).message)
  } finally {
    input.value = ''
  }
}

async function doSearch() {
  if (!keyword.value.trim()) {
    searchResults.value = []
    return
  }
  const { data } = await http.post('/knowledge/search', null, { params: { q: keyword.value } })
  searchResults.value = data ?? []
}

// ---------- 轻量 markdown 渲染（先转义再格式化，避免 XSS） ----------
function escapeHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
function inline(s: string) {
  return s
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
}
function renderMarkdown(md: string): string {
  const lines = escapeHtml(md).split(/\r?\n/)
  let html = ''
  let inCode = false
  let listType = ''
  const closeList = () => {
    if (listType) {
      html += `</${listType}>`
      listType = ''
    }
  }
  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      if (inCode) {
        html += '</code></pre>'
        inCode = false
      } else {
        closeList()
        html += '<pre><code>'
        inCode = true
      }
      continue
    }
    if (inCode) {
      html += line + '\n'
      continue
    }
    const h = /^(#{1,6})\s+(.*)$/.exec(line)
    if (h) {
      closeList()
      html += `<h${h[1].length}>${inline(h[2])}</h${h[1].length}>`
      continue
    }
    if (/^\s*[-*]\s+/.test(line)) {
      if (listType !== 'ul') {
        closeList()
        html += '<ul>'
        listType = 'ul'
      }
      html += `<li>${inline(line.replace(/^\s*[-*]\s+/, ''))}</li>`
      continue
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      if (listType !== 'ol') {
        closeList()
        html += '<ol>'
        listType = 'ol'
      }
      html += `<li>${inline(line.replace(/^\s*\d+\.\s+/, ''))}</li>`
      continue
    }
    if (/^\s*>\s?/.test(line)) {
      closeList()
      html += `<blockquote>${inline(line.replace(/^\s*>\s?/, ''))}</blockquote>`
      continue
    }
    if (/^\s*-{3,}\s*$/.test(line)) {
      closeList()
      html += '<hr/>'
      continue
    }
    if (line.trim() === '') {
      closeList()
      continue
    }
    closeList()
    html += `<p>${inline(line)}</p>`
  }
  closeList()
  if (inCode) html += '</code></pre>'
  return html
}

onMounted(loadTree)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>业务层 · 智能运维 · 运维知识库</b>
          <span class="muted">带头层级结构的 markdown 文档编辑器 / 上传转换为 markdown</span>
          <el-input v-model="keyword" placeholder="全文检索" size="small" style="width: 200px" clearable @keyup.enter="doSearch" />
          <el-button size="small" @click="doSearch">检索</el-button>
        </div>
      </template>

      <el-row :gutter="12">
        <el-col :span="7">
          <div class="pane">
            <div class="pane-actions">
              <el-button size="small" @click="createNode('FOLDER')">新建目录</el-button>
              <el-button size="small" type="primary" @click="createNode('DOC')">新建文档</el-button>
              <el-button size="small" @click="pickFile">上传</el-button>
              <el-button size="small" type="danger" :disabled="!selected" @click="removeNode">删除</el-button>
              <input ref="fileInput" type="file" class="hidden-file" @change="onFileChange" />
            </div>
            <el-tree
              v-loading="loading"
              :data="tree"
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              default-expand-all
              highlight-current
              @node-click="onNodeClick"
            >
              <template #default="{ data }">
                <span class="tree-node">
                  <span class="tag">{{ data.nodeType === 'FOLDER' ? '📁' : '📄' }}</span>
                  {{ data.name }}
                </span>
              </template>
            </el-tree>
            <div v-if="searchResults.length" class="search-hits">
              <div class="hits-title">检索结果（{{ searchResults.length }}）</div>
              <div v-for="hit in searchResults" :key="hit.id" class="hit" @click="onNodeClick({ id: hit.id, name: hit.name, parentId: 0, code: '', nodeType: 'DOC' })">
                <b>{{ hit.name }}</b>
                <div class="muted">{{ hit.snippet }}</div>
              </div>
            </div>
          </div>
        </el-col>

        <el-col :span="17">
          <div class="pane">
            <template v-if="isDoc">
              <div class="editor-head">
                <el-input v-model="editor.title" placeholder="文档标题" style="max-width: 320px" />
                <el-switch v-model="preview" active-text="预览" inactive-text="编辑" />
                <el-button type="primary" size="small" :loading="saving" @click="saveDoc">保存</el-button>
              </div>
              <el-input v-if="!preview" v-model="editor.content" type="textarea" :rows="22" class="md-input" />
              <div v-else class="md-preview" v-html="renderMarkdown(editor.content)" />
            </template>
            <el-empty v-else description="选择左侧文档开始编辑，或用上方按钮新建/上传" />
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: 0; }
.muted { color: #909399; font-size: 12px; }
.pane { border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; min-height: 520px; }
.pane-actions { display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.hidden-file { display: none; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
.tag { font-size: 12px; }
.editor-head { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.editor-head .el-button { margin-left: auto; }
.md-input :deep(textarea) { font-family: Consolas, Monaco, monospace; font-size: 13px; }
.md-preview { border: 1px solid #ebeef5; border-radius: 4px; padding: 12px 16px; min-height: 460px; }
.md-preview :deep(h1), .md-preview :deep(h2), .md-preview :deep(h3) { margin: 12px 0 8px; }
.md-preview :deep(pre) { background: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto; }
.md-preview :deep(blockquote) { border-left: 3px solid #dcdfe6; margin: 8px 0; padding-left: 10px; color: #606266; }
.search-hits { margin-top: 10px; border-top: 1px dashed #ebeef5; padding-top: 8px; }
.hits-title { font-size: 12px; color: #909399; margin-bottom: 6px; }
.hit { cursor: pointer; padding: 4px 0; }
.hit:hover { color: #409eff; }
</style>
