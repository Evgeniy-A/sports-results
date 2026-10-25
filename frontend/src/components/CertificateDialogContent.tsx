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
      <h3><span className="certificate-step">1</span>Выберите дизайн</h3>
      <div className="certificate-template-options">{availability.templates.map((template) => <label
        className={`certificate-option${templateId === template.id ? ' is-selected' : ''}`} key={template.id}>
        <input type="radio" name="certificate-template" checked={templateId === template.id}
          onChange={() => setTemplateId(template.id)} />
        <span className="certificate-option-copy"><strong>{template.displayName}</strong></span>
      </label>)}</div>
    </section>
    <section className="certificate-section">
      <h3><span className="certificate-step">2</span>Что добавить?</h3>
      <p className="certificate-info-note"><strong>Автоматически добавим</strong>
        <span>ФИО или название команды · мероприятие · город · дистанцию</span>
      </p>
      <div className="certificate-field-options">{availability.fields.map((option) => <label
        className={`certificate-option certificate-field-option${option.available ? '' : ' is-disabled'}`} key={option.field}>
        <input type="checkbox" checked={fields.includes(option.field)} disabled={!option.available}
          onChange={() => toggle(option.field)} />
        <span className="certificate-option-copy"><strong>{option.label}</strong>
          {!option.available && <small>Нет данных для этого результата</small>}
        </span>
      </label>)}</div>
    </section>
    {error && <p className="state-message compact error" role="alert">{error}</p>}
    <div className="certificate-actions">
      <button type="button" className="certificate-back-button" onClick={onBack}>Назад</button>
      <button type="button" className="primary-action certificate-download-button" disabled={busy || !templateId}
        onClick={() => void generate()}>
        {busy ? 'Создаём…' : 'Скачать PDF'}
      </button>
    </div>
  </div>
}
