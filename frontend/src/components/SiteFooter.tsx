export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer-brand">
        <span className="brand-mark" aria-hidden="true">SR</span>
        <div>
          <strong>Спортрезультаты</strong>
          <span>Старт. Финиш. Результат.</span>
        </div>
      </div>
      <nav aria-label="Разделы в подвале">
        <a href="/events">Мероприятия</a>
        <a href="/results">Результаты</a>
        <a href="/appeal">Обращение</a>
      </nav>
      <p>Официальные протоколы и сервисы для участников</p>
    </footer>
  )
}
