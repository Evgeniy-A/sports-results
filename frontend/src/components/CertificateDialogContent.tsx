import { useState } from 'react'
import { api, ApiError } from '../api/client'
import type { CertificateAvailability, CertificateField } from '../api/types'

export function CertificateDialogContent({ resultId, availability, onBack }: {
  resultId: number
  availability: CertificateAvailability
  onBack: () => void
}) {
  const [templateId, setTemplateId] = useState(availability.templates[0]?.id ?? 0)
  const [fields, setFields] = useState<CertificateField[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const toggle = (field: CertificateField) => setFields((current) => current.includes(field)
    ? current.filter((item) => item !== field)
    : [...current, field])

  const generate = async () => {
    setBusy(true); setError(null)
    try {
      const blob = await api.generateCertificate(resultId, templateId, fields)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url; link.download = `certificate-result-${resultId}.pdf`; link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (reason) {
      setError(reason instanceof ApiError && reason.code === 'CERTIFICATE_CAPACITY_EXCEEDED'
        ? 'Сервис занят. Пожалуйста, повторите через несколько секунд.'
        : 'Не удалось создать сертификат. Попробуйте ещё раз.')
    } finally { setBusy(false) }
  }

  return <div className="certificate-dialog-content">
    <p className="eyebrow">Персональный PDF</p>
    <h2 id="result-title">Создать сертификат</h2>
    <section className="certificate-section">
      <h3>Выберите дизайн</h3>
      <div className="certificate-template-options">{availability.templates.map((template) => <label key={template.id}>
        <input type="radio" name="certificate-template" checked={templateId === template.id}
          onChange={() => setTemplateId(template.id)} /> <span>{template.displayName}</span>
      </label>)}</div>
    </section>
    <section className="certificate-section">
      <h3>Что добавить?</h3>
      <p>В сертификат автоматически войдут: ФИО или название команды, мероприятие, город и дистанция.</p>
      <div className="certificate-field-options">{availability.fields.map((option) => <label key={option.field}>
        <input type="checkbox" checked={fields.includes(option.field)} disabled={!option.available}
          onChange={() => toggle(option.field)} />
        <span>{option.label}{!option.available ? ' — нет данных' : ''}</span>
      </label>)}</div>
    </section>
    {error && <p className="state-message compact error" role="alert">{error}</p>}
    <div className="certificate-actions">
      <button type="button" onClick={onBack}>Назад</button>
      <button type="button" className="primary-action" disabled={busy || !templateId} onClick={() => void generate()}>
        {busy ? 'Создаём…' : 'Скачать PDF'}
      </button>
    </div>
  </div>
}
