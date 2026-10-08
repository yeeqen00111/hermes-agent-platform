<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { streamChat } from '@/api/chat'

interface ToolEvt {
  tool: string
  success: boolean
  error?: string
}

interface ChatMsg {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  tools: ToolEvt[]
}

interface ApprovalCard {
  requestId: string
  tool: string
  toolName: string
  safetyLevel: string
  arguments: unknown
  expireTime: string
}

interface AgentProfile {
  agentCode: string
  name?: string
  executionMode?: string
  status?: string
}

const agents = ref<AgentProfile[]>([])
const agentCode = ref('')
const sessionId = ref(crypto.randomUUID())
const input = ref('')
const messages = ref<ChatMsg[]>([])
const approvals = ref<ApprovalCard[]>([])
const sending = ref(false)
const scrollBox = ref<HTMLElement | null>(null)

let seq = 0
let controller: AbortController | null = null

async function loadAgents() {
  try {
    const { data } = await http.get<AgentProfile[]>('/agent-profiles')
    agents.value = data ?? []
    if (agents.value.length && !agentCode.value) {
      agentCode.value = agents.value[0].agentCode
    }
  } catch (e) {
    ElMessage.error('加载智能体失败（后端 8081 是否已启动？）：' + (e as Error).message)
  }
}

async function loadPending() {
  try {
    const { data } = await http.get<ApprovalCard[]>('/approvals/pending', {
      params: { sessionId: sessionId.value },
    })
    approvals.value = data ?? []
  } catch {
    /* 审批查询失败不阻塞对话 */
  }
}

function scrollDown() {
  nextTick(() => {
    const el = scrollBox.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function push(role: 'USER' | 'ASSISTANT'): ChatMsg {
  const msg: ChatMsg = { id: ++seq, role, content: '', tools: [] }
  messages.value.push(msg)
  scrollDown()
  return msg
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  if (!agentCode.value) {
    ElMessage.warning('请先选择智能体')
    return
  }
  input.value = ''
  push('USER').content = text
  const reply = push('ASSISTANT')
  sending.value = true
  controller = new AbortController()

  try {
    await streamChat(
      { sessionId: sessionId.value, agentCode: agentCode.value, message: text },
      {
        signal: controller.signal,
        onEvent(event, data) {
          if (event === 'message.delta') {
            reply.content += typeof data === 'string' ? data : String((data as { text?: string })?.text ?? '')
            scrollDown()
          } else if (event === 'tool.start' || event === 'tool.complete') {
            const d = data as { tool?: string; success?: boolean; error?: string }
            reply.tools.push({ tool: d?.tool ?? '工具', success: !!d?.success, error: d?.error })
            scrollDown()
          } else if (event === 'approval.request') {
            approvals.value.push(data as ApprovalCard)
          } else if (event === 'approval.cancel') {
            const d = data as { requestId?: string }
            approvals.value = approvals.value.filter((a) => a.requestId !== d?.requestId)
          } else if (event === 'chat.error') {
            const d = data as { message?: string; errorCode?: string }
            reply.content += `\n[错误] ${d?.message ?? d?.errorCode ?? '未知错误'}`
          }
        },
      },
    )
  } catch (e) {
    if ((e as Error)?.name !== 'AbortError') {
      ElMessage.error('请求失败：' + (e as Error).message)
    }
  } finally {
    sending.value = false
    controller = null
  }
}

async function respond(card: ApprovalCard, choice: string) {
  try {
    const { data } = await http.post<{ success: boolean; message?: string }>(
      `/approvals/${card.requestId}/respond`,
      { choice },
    )
    if (data?.success) ElMessage.success('已应答：' + choice)
    else ElMessage.warning(data?.message ?? '应答未生效')
    approvals.value = approvals.value.filter((a) => a.requestId !== card.requestId)
  } catch (e) {
    ElMessage.error('应答失败：' + (e as Error).message)
  }
}

async function stop() {
  controller?.abort()
  try {
    await http.post(`/sessions/${sessionId.value}/interrupt`)
  } catch {
    /* ignore */
  }
  sending.value = false
}

function resetSession() {
  sessionId.value = crypto.randomUUID()
  messages.value = []
  approvals.value = []
  ElMessage.success('已新建会话')
}

onMounted(() => {
  loadAgents()
  loadPending()
})
</script>

<template>
  <div class="chat">
    <div class="toolbar">
      <el-select v-model="agentCode" placeholder="选择智能体" style="width: 240px" size="small">
        <el-option
          v-for="a in agents"
          :key="a.agentCode"
          :label="`${a.name ?? a.agentCode}（${a.executionMode ?? '-'}）`"
          :value="a.agentCode"
        />
      </el-select>
      <span class="sid">会话：{{ sessionId.slice(0, 8) }}</span>
      <el-button size="small" @click="resetSession">新建会话</el-button>
    </div>

    <div ref="scrollBox" class="stream">
      <el-empty v-if="messages.length === 0" description="开始与智能体对话（SSE 流式，契约 §3.2）" />
      <div v-for="m in messages" :key="m.id" class="msg" :class="m.role.toLowerCase()">
        <div class="bubble">
          <div class="role">{{ m.role === 'USER' ? '我' : '智能体' }}</div>
          <div class="content">{{ m.content || (sending ? '…' : '') }}</div>
          <div v-if="m.tools.length" class="tools">
            <el-tag
              v-for="(t, i) in m.tools"
              :key="i"
              size="small"
              :type="t.success ? 'success' : 'danger'"
              class="tool-tag"
            >
              {{ t.tool }}{{ t.error ? ' · ' + t.error : '' }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <div v-if="approvals.length" class="approvals">
      <el-card v-for="card in approvals" :key="card.requestId" class="approval-card" shadow="never">
        <div class="approval-head">
          <el-tag type="warning" size="small">{{ card.safetyLevel }}</el-tag>
          <b>{{ card.toolName || card.tool }}</b>
          <span class="muted">过期：{{ card.expireTime }}</span>
        </div>
        <pre class="args">{{ JSON.stringify(card.arguments, null, 2) }}</pre>
        <div class="approval-actions">
          <el-button size="small" type="primary" @click="respond(card, 'allow_once')">允许一次</el-button>
          <el-button size="small" @click="respond(card, 'allow_session')">允许本次会话</el-button>
          <el-button size="small" @click="respond(card, 'allow_always')">始终允许</el-button>
          <el-button size="small" type="danger" @click="respond(card, 'deny')">拒绝</el-button>
        </div>
      </el-card>
    </div>

    <div class="composer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        @keydown.enter.exact.prevent="send"
      />
      <div class="composer-actions">
        <el-button v-if="sending" type="danger" plain @click="stop">停止</el-button>
        <el-button type="primary" :loading="sending" @click="send">发送</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sid {
  color: #909399;
  font-size: 12px;
}
.stream {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 16px;
}
.msg {
  display: flex;
  margin-bottom: 14px;
}
.msg.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 72%;
  background: #f0f2f5;
  border-radius: 8px;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.user .bubble {
  background: #ecf5ff;
}
.role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.tools {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.approvals {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.approval-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.args {
  background: #fafafa;
  border-radius: 4px;
  padding: 8px;
  font-size: 12px;
  max-height: 160px;
  overflow: auto;
}
.approval-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}
.muted {
  color: #909399;
  font-size: 12px;
  margin-left: auto;
}
.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.composer-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
