import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { statusRussian } from '../utils'

export function StatusBadge({ value }: { value: string }) {
  const tone = ['PUBLISHED', 'RESOLVED', 'CLEAN', 'finished', 'OPEN'].includes(value)
    ? 'success'
    : ['DRAFT', 'NEW', 'PENDING', 'PENDING_UPLOAD'].includes(value)
      ? 'neutral'
      : ['REJECTED', 'INFECTED', 'BLOCKING', 'INVALID'].includes(value)
        ? 'danger'
        : 'warning'
  return <span className={`admin-badge admin-badge-${tone}`}>{statusRussian(value)}</span>
}

export function AdminNotice({ tone = 'info', children }: { tone?: 'info' | 'warning' | 'danger' | 'success'; children: ReactNode }) {
  return <div className={`admin-notice admin-notice-${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>{children}</div>
}

export function Loadable({ loading, error, empty, children }: {
  loading: boolean
  error: string | null
  empty?: boolean
  children: ReactNode
}) {
  if (loading) return <div className="admin-state" aria-live="polite"><span className="admin-spinner" /> Загрузка…</div>
  if (error) return <AdminNotice tone="danger">{error}</AdminNotice>
  if (empty) return <div className="admin-empty">Данных пока нет.</div>
  return <>{children}</>
}

export function AdminPagination({ page, totalPages, onChange }: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return <nav className="admin-pagination" aria-label="Страницы">
    <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>Назад</button>
    <span>{page + 1} из {totalPages}</span>
    <button type="button" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Далее</button>
  </nav>
}

export function ConfirmDialog({ title, description, confirmLabel = 'Подтвердить', danger = false, busy = false, children, onConfirm, onClose }: {
  title: string
  description: string
  confirmLabel?: string
  danger?: boolean
  busy?: boolean
  children?: ReactNode
  onConfirm: () => void
  onClose: () => void
}) {
  const cancelRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    cancelRef.current?.focus()
    const close = (event: KeyboardEvent) => event.key === 'Escape' && !busy && onClose()
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [busy, onClose])
  return <div className="admin-modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && !busy && onClose()}>
    <section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="admin-confirm-title">
      <h2 id="admin-confirm-title">{title}</h2>
      <p>{description}</p>
      {children}
      <div className="admin-modal-actions">
        <button ref={cancelRef} className="admin-button-secondary" type="button" disabled={busy} onClick={onClose}>Отмена</button>
        <button className={danger ? 'admin-button-danger' : 'admin-button-primary'} type="button" disabled={busy} onClick={onConfirm}>
          {busy ? 'Выполняется…' : confirmLabel}
        </button>
      </div>
    </section>
  </div>
}

export function Drawer({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const closeRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    closeRef.current?.focus()
    const close = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [onClose])
  return <div className="admin-drawer-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <aside className="admin-drawer" role="dialog" aria-modal="true" aria-labelledby="admin-drawer-title">
      <header><h2 id="admin-drawer-title">{title}</h2><button ref={closeRef} type="button" onClick={onClose} aria-label="Закрыть">×</button></header>
      <div className="admin-drawer-body">{children}</div>
    </aside>
  </div>
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return <label className="admin-field"><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>
}
