import { PublicBrandMark } from './PublicBrandMark'

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer-brand">
        <PublicBrandMark />
        <span className="brand-divider" aria-hidden="true" />
        <div>
          <strong>Спортивные результаты</strong>
          <span>Старт. Финиш. Результат.</span>
        </div>
      </div>
      <nav aria-label="Разделы в подвале">
        <a href="/results">Результаты</a>
        <a href="/appeal">Обращение</a>
      </nav>
      <p>Официальные протоколы и сервисы для участников</p>
    </footer>
  )
}
