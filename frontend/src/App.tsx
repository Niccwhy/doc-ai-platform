import { useEffect, useState } from 'react'
import './App.css'

/** 后端统一返回体 */
type ApiResult<T> = { code: number; message: string; data: T }

type ChangeRecord = {
  scope: string
  action: string
  detail: string
  affected: number
}

type DocPreview = {
  token: string | null
  docId: string
  intent: string
  message: string
  changes: ChangeRecord[]
  affected: number
  hasChanges: boolean
}

type VersionInfo = { version: number; note: string; size: number; createdAt: string }

const API = '/api'

async function apiJson<T>(url: string, init?: RequestInit): Promise<T> {
  const resp = await fetch(url, init)
  const body = (await resp.json()) as ApiResult<T>
  if (!body || body.code !== 0) {
    throw new Error(body?.message || `请求失败（HTTP ${resp.status}）`)
  }
  return body.data
}

async function pollTask(
  taskId: string,
  onTick: (progress: number, status: string) => void,
  maxTries = 150,
): Promise<{ status: string; result?: string; errorMsg?: string }> {
  for (let i = 0; i < maxTries; i++) {
    const task = await apiJson<{ status: string; progress: number; result?: string; errorMsg?: string }>(
      `${API}/tasks/${taskId}`,
    )
    onTick(task.progress, task.status)
    if (task.status === 'SUCCESS' || task.status === 'FAILED') {
      return task
    }
    await new Promise((resolve) => setTimeout(resolve, 700))
  }
  throw new Error('任务等待超时')
}

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function App() {
  // 服务状态
  const [health, setHealth] = useState('检测中')
  const [llmStatus, setLlmStatus] = useState('检测中')

  // 样本建库
  const [sampleFiles, setSampleFiles] = useState<File[]>([])
  const [ingestProgress, setIngestProgress] = useState(0)
  const [ingestStatus, setIngestStatus] = useState('')
  const [ingestMessage, setIngestMessage] = useState('')

  // 模板填表
  const [templateFile, setTemplateFile] = useState<File | null>(null)
  const [fillMessage, setFillMessage] = useState('')
  const [fillBusy, setFillBusy] = useState(false)

  // 文档操作
  const [docFile, setDocFile] = useState<File | null>(null)
  const [instruction, setInstruction] = useState('把全文的标题加粗')
  const [preview, setPreview] = useState<DocPreview | null>(null)
  const [versions, setVersions] = useState<VersionInfo[]>([])
  const [docMessage, setDocMessage] = useState('')
  const [docBusy, setDocBusy] = useState(false)

  // 版本列表所属文档（预览后才知道，用于下载与回滚）
  const versionsDocId = preview?.docId ?? ''

  useEffect(() => {
    fetch(`${API}/health`)
      .then((r) => r.json())
      .then((d) => setHealth(d.status ?? '未知'))
      .catch(() => setHealth('不可用'))

    apiJson<{ available: boolean; client: string }>(`${API}/extract/status`)
      .then((d) => setLlmStatus(d.available ? `真实调用（${d.client}）` : '未配置 Key（Mock 降级）'))
      .catch(() => setLlmStatus('不可用'))
  }, [])

  async function handleIngest() {
    if (sampleFiles.length === 0) {
      setIngestMessage('请先选择样本文件')
      return
    }
    setIngestMessage('')
    setIngestProgress(0)
    setIngestStatus('提交中')
    try {
      const form = new FormData()
      sampleFiles.forEach((file) => form.append('files', file))
      const { taskId } = await apiJson<{ taskId: string }>(`${API}/ingest`, {
        method: 'POST',
        body: form,
      })
      const task = await pollTask(taskId, (p, s) => {
        setIngestProgress(p)
        setIngestStatus(s)
      })
      if (task.status === 'SUCCESS') {
        setIngestStatus('完成')
        setIngestProgress(100)
        setIngestMessage(`已入库 ${sampleFiles.length} 个文件`)
      } else {
        setIngestStatus('失败')
        setIngestMessage(task.errorMsg || '入库失败')
      }
    } catch (e) {
      setIngestStatus('失败')
      setIngestMessage(e instanceof Error ? e.message : '未知错误')
    }
  }

  async function handleFill() {
    if (!templateFile) {
      setFillMessage('请先选择模板文件')
      return
    }
    setFillBusy(true)
    setFillMessage('正在填表…')
    try {
      const form = new FormData()
      form.append('file', templateFile)
      const resp = await fetch(`${API}/fill/export`, { method: 'POST', body: form })
      if (!resp.ok) {
        const err = await resp.json().catch(() => null)
        throw new Error(err?.message || `填表失败（HTTP ${resp.status}）`)
      }
      const count = resp.headers.get('X-Fill-Count') ?? '0'
      const unmatched = decodeURIComponent(resp.headers.get('X-Fill-Unmatched') ?? '')
      saveBlob(await resp.blob(), `已填写-${templateFile.name}`)
      setFillMessage(
        `已填写 ${count} 个字段并下载` + (unmatched ? `；未匹配：${unmatched}` : ''),
      )
    } catch (e) {
      setFillMessage(e instanceof Error ? e.message : '未知错误')
    } finally {
      setFillBusy(false)
    }
  }

  async function handlePreview() {
    if (!docFile) {
      setDocMessage('请先选择 .docx 文件')
      return
    }
    setDocBusy(true)
    setPreview(null)
    setDocMessage('')
    try {
      const form = new FormData()
      form.append('file', docFile)
      form.append('instruction', instruction)
      const data = await apiJson<DocPreview>(`${API}/doc/preview`, { method: 'POST', body: form })
      setPreview(data)
      setDocMessage(data.message)
      await refreshVersions(data.docId)
    } catch (e) {
      setDocMessage(e instanceof Error ? e.message : '未知错误')
    } finally {
      setDocBusy(false)
    }
  }

  async function handleApply() {
    if (!preview?.token) {
      return
    }
    setDocBusy(true)
    try {
      const data = await apiJson<{ docId: string; version: number }>(`${API}/doc/apply`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: preview.token }),
      })
      setDocMessage(`已保存为 v${data.version}`)
      setPreview(null)
      await refreshVersions(data.docId)
    } catch (e) {
      setDocMessage(e instanceof Error ? e.message : '未知错误')
    } finally {
      setDocBusy(false)
    }
  }

  async function refreshVersions(docId: string) {
    if (!docId) {
      return
    }
    try {
      setVersions(await apiJson<VersionInfo[]>(`${API}/doc/${encodeURIComponent(docId)}/versions`))
    } catch {
      setVersions([])
    }
  }

  async function handleDownloadPreview() {
    if (!preview?.token) {
      return
    }
    const resp = await fetch(`${API}/doc/preview/${preview.token}/download`)
    saveBlob(await resp.blob(), `预览-${preview.docId}.docx`)
  }

  async function handleRollback(version: number) {
    if (!versionsDocId) {
      setDocMessage('请先执行一次预览，确定文档标识')
      return
    }
    setDocBusy(true)
    try {
      const data = await apiJson<{ newVersion: number }>(`${API}/doc/${encodeURIComponent(versionsDocId)}/rollback`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version }),
      })
      setDocMessage(`已回滚到 v${version}（生成 v${data.newVersion}）`)
      await refreshVersions(versionsDocId)
    } catch (e) {
      setDocMessage(e instanceof Error ? e.message : '未知错误')
    } finally {
      setDocBusy(false)
    }
  }

  return (
    <div className="page">
      <header className="hero-card">
        <div>
          <h1>文档理解与多源数据融合</h1>
          <p className="subtitle">上传样本 → 自动建库 → 模板填表 → 自然语言改文档</p>
        </div>
        <div className="status-group">
          <span className={`badge ${health === 'UP' ? 'ok' : 'warn'}`}>服务：{health}</span>
          <span className={`badge ${llmStatus.startsWith('真实') ? 'ok' : 'warn'}`}>模型：{llmStatus}</span>
        </div>
      </header>

      <section className="panel">
        <h2>1 · 样本批量建库</h2>
        <p className="hint">一次选择全部样本（docx / xlsx / md / txt），系统解析并建立跨文档索引。</p>
        <input
          type="file"
          multiple
          accept=".docx,.xlsx,.md,.txt"
          onChange={(e) => setSampleFiles(Array.from(e.target.files ?? []))}
        />
        <div className="row">
          <button onClick={handleIngest} disabled={sampleFiles.length === 0}>
            上传并建库（{sampleFiles.length} 个文件）
          </button>
          <span className="hint">
            {ingestStatus} {ingestStatus ? `${ingestProgress}%` : ''}
          </span>
        </div>
        {ingestMessage && <p className="result">{ingestMessage}</p>}
      </section>

      <section className="panel">
        <h2>2 · 模板表格自动填写</h2>
        <p className="hint">上传 Excel / Word 模板，系统识别表头、检索样本数据、按坐标回填并下载。</p>
        <input
          type="file"
          accept=".xlsx,.docx"
          onChange={(e) => setTemplateFile(e.target.files?.[0] ?? null)}
        />
        <div className="row">
          <button onClick={handleFill} disabled={!templateFile || fillBusy}>
            {fillBusy ? '处理中…' : '填表并下载'}
          </button>
        </div>
        {fillMessage && <p className="result">{fillMessage}</p>}
      </section>

      <section className="panel">
        <h2>3 · 自然语言文档操作</h2>
        <p className="hint">
          示例：全文加粗 / 所有段落居中 / 第2段字号改为小四 / 把甲方替换为乙方
        </p>
        <input type="file" accept=".docx" onChange={(e) => setDocFile(e.target.files?.[0] ?? null)} />
        <div className="row">
          <input
            className="instruction"
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
            placeholder="输入指令"
          />
          <button onClick={handlePreview} disabled={!docFile || docBusy}>
            试执行预览
          </button>
        </div>

        {preview && (
          <div className="preview-box">
            <p>
              意图：<strong>{preview.intent}</strong> · 影响 {preview.affected} 处
            </p>
            {preview.changes.length > 0 && (
              <ul className="change-list">
                {preview.changes.map((c, i) => (
                  <li key={i}>
                    <span className="tag">{c.scope}</span> {c.detail}
                  </li>
                ))}
              </ul>
            )}
            <div className="row">
              <button onClick={handleDownloadPreview} disabled={!preview.token} className="secondary">
                下载预览文件
              </button>
              <button onClick={handleApply} disabled={!preview.token || docBusy}>
                确认应用
              </button>
            </div>
          </div>
        )}

        {docMessage && <p className="result">{docMessage}</p>}

        {versions.length > 0 && (
          <div className="versions">
            <h3>版本记录</h3>
            <table>
              <thead>
                <tr>
                  <th>版本</th>
                  <th>说明</th>
                  <th>大小</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {versions.map((v) => (
                  <tr key={v.version}>
                    <td>v{v.version}</td>
                    <td>{v.note}</td>
                    <td>{Math.round(v.size / 1024)} KB</td>
                    <td className="actions">
                      <a href={`${API}/doc/${encodeURIComponent(versionsDocId)}/versions/${v.version}/download`}>
                        下载
                      </a>
                      <button className="link" onClick={() => handleRollback(v.version)}>
                        回滚
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}

export default App
