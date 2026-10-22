import type { ReactNode } from 'react'
import { AdminLink } from '../router'

export interface AdminCreationStepItem {
  number: string
  label: string
}

export function AdminCreationHeader({ title, description, steps, activeStep }: {
  title: string
  description: string
  steps: AdminCreationStepItem[]
  activeStep: string
}) {
  return <header className="admin-creation-header">
    <nav className="admin-breadcrumbs" aria-label="Навигационная цепочка">
      <AdminLink href="/admin/events">Мероприятия</AdminLink>
      <span aria-hidden="true">/</span>
      <span aria-current="page">{title}</span>
    </nav>
    <div className="admin-creation-heading"><div><p className="admin-eyebrow">Создание мероприятия</p><h1>{title}</h1><p>{description}</p></div></div>
    <ol className="admin-creation-progress" aria-label="Этапы создания">
      {steps.map((step) => <li className={step.number === activeStep ? 'is-active' : ''} key={step.number}>
        <span>{step.number}</span><strong>{step.label}</strong>
      </li>)}
    </ol>
  </header>
}

export function AdminCreationSection({ number, title, description, muted = false, children }: {
  number: string
  title: string
  description: string
  muted?: boolean
  children: ReactNode
}) {
  return <section className={`admin-creation-section${muted ? ' is-muted' : ''}`}>
    <header><span>{number}</span><div><h2>{title}</h2><p>{description}</p></div></header>
    <div className="admin-creation-section-body">{children}</div>
  </section>
}
