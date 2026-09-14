export type PreparedAttachmentState = 'SELECTED' | 'UPLOADING' | 'UPLOADED' | 'ERROR'

export interface PreparedAttachment {
  localId: string
  fileName: string
  sizeBytes: number
  progress: number | null
  state: PreparedAttachmentState
  errorMessage?: string | null
  retryable?: boolean
}

interface Props {
  enabled: boolean
  attachments: PreparedAttachment[]
  onSelect: (files: FileList) => void
  onRetry?: (localId: string) => void
  onRemove?: (localId: string) => void
  disabled?: boolean
  maxFileSizeBytes?: number
  maxAttachments?: number
}

const STATE_LABELS: Record<PreparedAttachmentState, string> = {
  SELECTED: 'Готов к загрузке',
  UPLOADING: 'Загружается',
  UPLOADED: 'Файл загружен. Проверяется.',
  ERROR: 'Ошибка загрузки',
}

function formatFileSize(sizeBytes: number): string {
  const megabytes = sizeBytes / 1024 / 1024
  return `${megabytes < 10 ? megabytes.toFixed(1) : Math.round(megabytes)} МБ`
}

export function ResultIssueAttachmentField({
  enabled,
  attachments,
  onSelect,
  onRetry,
  onRemove,
  disabled = false,
  maxFileSizeBytes,
  maxAttachments,
}: Props) {
  if (!enabled) return null

  return <section className="issue-attachments" aria-labelledby="issue-attachments-title">
    <h3 id="issue-attachments-title">Подтверждающие материалы <small>необязательно</small></h3>
    {!disabled && <label className="attachment-picker">
      <span>Добавить файл</span>
      <input type="file" multiple onChange={(event) => {
        if (event.target.files) onSelect(event.target.files)
        event.target.value = ''
      }} />
    </label>}
    {(maxFileSizeBytes || maxAttachments) && <p className="attachment-limits">
      {maxAttachments ? `До ${maxAttachments} файлов` : ''}
      {maxAttachments && maxFileSizeBytes ? ', ' : ''}
      {maxFileSizeBytes ? `каждый до ${formatFileSize(maxFileSizeBytes)}` : ''}.
    </p>}
    {attachments.length > 0 && <ul>{attachments.map((attachment) => <li key={attachment.localId}>
      <span className="attachment-name">
        <strong>{attachment.fileName}</strong>
        <small>{formatFileSize(attachment.sizeBytes)}</small>
      </span>
      <span className={`attachment-status attachment-status-${attachment.state.toLowerCase()}`}>
        {STATE_LABELS[attachment.state]}
        {attachment.state === 'UPLOADING' && attachment.progress !== null
          ? ` ${attachment.progress}%`
          : ''}
        {attachment.state === 'UPLOADING' && attachment.progress !== null
          && <progress max="100" value={attachment.progress} aria-label={`Загрузка ${attachment.fileName}`} />}
        {attachment.errorMessage && <small role="alert">{attachment.errorMessage}</small>}
        {attachment.state === 'ERROR' && attachment.retryable && onRetry
          && <button type="button" className="attachment-retry" onClick={() => onRetry(attachment.localId)}>Повторить</button>}
        {!disabled && (attachment.state === 'SELECTED' || attachment.state === 'ERROR') && onRemove
          && <button type="button" className="attachment-remove" onClick={() => onRemove(attachment.localId)}>Убрать</button>}
      </span>
    </li>)}</ul>}
  </section>
}
