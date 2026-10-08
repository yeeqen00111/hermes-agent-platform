/**
 * Chat SSE 消费端。
 * 契约 §3.2：POST /api/chat 返回 text/event-stream；事件名小写点号。
 */
export interface ChatRequest {
  sessionId: string
  agentCode: string
  message: string
}

export interface SseHandlers {
  /** 收到一个 SSE 事件；data 已尽量 JSON 解析，失败则回退为原始文本 */
  onEvent: (event: string, data: unknown) => void
  onDone?: () => void
  signal?: AbortSignal
}

export async function streamChat(req: ChatRequest, handlers: SseHandlers): Promise<void> {
  const res = await fetch('/api/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(req),
    signal: handlers.signal,
  })

  if (!res.ok || !res.body) {
    throw new Error(`chat HTTP ${res.status}`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let sep = buffer.indexOf('\n\n')
    while (sep >= 0) {
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      const { event, data } = parseBlock(block)
      if (event) handlers.onEvent(event, data)
      sep = buffer.indexOf('\n\n')
    }
  }

  handlers.onDone?.()
}

function parseBlock(block: string): { event: string; data: unknown } {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim())
    }
  }
  const text = dataLines.join('\n')
  let data: unknown = text
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }
  return { event, data }
}
