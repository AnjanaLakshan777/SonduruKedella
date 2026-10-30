import { useRef, useState } from 'react'
import { CircleAlert, CircleCheck, Download, FileText, FileUp, RefreshCw, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { formatBytes } from '@/lib/format'
import { formatDate } from '@/lib/dates'
import { errorMessage } from '@/lib/api'
import type { AgreementDoc } from '@/data/types'

type Phase = { kind: 'idle' } | { kind: 'uploading'; name: string; size: number; progress: number } | { kind: 'error'; message: string; name?: string }

const MAX = 20 * 1024 * 1024

/**
 * Agreement PDF upload to the project's private storage. The server checks the file again (PDF signature, size)
 * and only ever serves it as a download to signed-in users.
 */
export function FileDropzone({ doc, onUpload, downloadUrl, onRemove, title = 'Drop the signed agreement PDF here', compact }: {
  doc?: AgreementDoc
  onUpload: (file: File) => Promise<void>
  downloadUrl?: string
  onRemove?: () => void
  title?: string
  compact?: boolean
}) {
  const input = useRef<HTMLInputElement>(null)
  const [phase, setPhase] = useState<Phase>({ kind: 'idle' })
  const [drag, setDrag] = useState(false)
  const [replacing, setReplacing] = useState(false)

  async function start(file: File) {
    const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
    if (!isPdf) return setPhase({ kind: 'error', message: 'Only PDF files can be uploaded', name: file.name })
    if (file.size > MAX) return setPhase({ kind: 'error', message: `The file is ${formatBytes(file.size)} — the limit is 20 MB`, name: file.name })
    // fetch can't report upload progress, so the bar just shows it's busy
    setPhase({ kind: 'uploading', name: file.name, size: file.size, progress: 60 })
    try {
      await onUpload(file)
      setPhase({ kind: 'idle' })
      setReplacing(false)
      toast.success('Agreement uploaded', { description: file.name })
    } catch (e) {
      setPhase({ kind: 'error', message: errorMessage(e), name: file.name })
    }
  }
  const pick = () => input.current?.click()
  const hidden = (
    <input ref={input} type="file" accept="application/pdf,.pdf" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) start(f); e.target.value = '' }} />
  )

  if (phase.kind === 'uploading') {
    return (
      <div className="bg-card flex items-center gap-3 rounded-lg border px-4 py-3">
        {hidden}
        <div className="bg-muted grid size-10 shrink-0 place-items-center rounded-md"><FileText className="size-5" /></div>
        <div className="grid min-w-0 flex-1 gap-1.5">
          <div className="truncate font-medium">{phase.name}</div>
          <div className="text-muted-foreground text-xs">{formatBytes(phase.size)} · Uploading…</div>
          <Progress value={phase.progress} aria-label="Upload progress" />
        </div>
      </div>
    )
  }

  if (doc && !replacing && phase.kind === 'idle') {
    // the server always sends it as an attachment, so this is a download either way
    const open = () => {
      if (!downloadUrl) return
      const a = document.createElement('a')
      a.href = downloadUrl
      a.rel = 'noopener'
      a.click()
    }
    return (
      <div className="bg-card flex flex-wrap items-center gap-3 rounded-lg border px-4 py-3">
        {hidden}
        <div className="bg-success-soft text-success grid size-10 shrink-0 place-items-center rounded-md"><CircleCheck className="size-5" /></div>
        <div className="grid min-w-0 flex-1 gap-0.5">
          <div className="truncate font-medium" title={doc.fileName}>{doc.fileName}</div>
          <div className="text-muted-foreground text-xs">{formatBytes(doc.size)} · Uploaded {formatDate(doc.uploadedAt)} · Private to this project</div>
        </div>
        <div className="flex flex-wrap gap-1">
          <Button variant="ghost" size="sm" onClick={open} disabled={!downloadUrl}><Download />Download</Button>
          <Button variant="outline" size="sm" onClick={() => setReplacing(true)}><RefreshCw />Replace</Button>
          {onRemove && <Button variant="ghost" size="sm" className="text-destructive" onClick={onRemove}>Remove</Button>}
        </div>
      </div>
    )
  }

  const err = phase.kind === 'error'
  return (
    <div
      onDragOver={(e) => { e.preventDefault(); setDrag(true) }}
      onDragLeave={() => setDrag(false)}
      onDrop={(e) => { e.preventDefault(); setDrag(false); const f = e.dataTransfer.files?.[0]; if (f) start(f) }}
      className={cn(
        'grid justify-items-center gap-2 rounded-lg border border-dashed border-muted-foreground/70 bg-muted/40 px-6 text-center transition-colors',
        compact ? 'py-6' : 'py-8',
        drag && 'border-primary-text bg-primary-soft',
        err && 'border-destructive bg-destructive-soft/60 border-solid',
      )}
    >
      {hidden}
      <div className={cn('bg-card mb-1 grid size-10 place-items-center rounded-full border', err && 'text-destructive')}>{err ? <CircleAlert className="size-5" /> : <FileUp className="size-5" />}</div>
      <div className={cn('font-medium', err && 'text-destructive-soft-foreground')}>{err ? phase.message : title}</div>
      <div className={cn('text-muted-foreground mb-2 text-xs', err && 'text-destructive-soft-foreground')}>
        {err ? `${phase.name ? `${phase.name} was not uploaded. ` : ''}Check the file and try again.` : 'PDF only, up to 20 MB. Stored privately with this project.'}
      </div>
      <div className="flex gap-2">
        <Button size="sm" variant={err ? 'outline' : 'default'} onClick={pick}>{err ? <RefreshCw /> : <Upload />}{err ? 'Try again' : 'Upload PDF'}</Button>
        {replacing && <Button size="sm" variant="ghost" onClick={() => setReplacing(false)}>Keep current file</Button>}
      </div>
    </div>
  )
}
