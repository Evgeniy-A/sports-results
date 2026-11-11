export function SiteHeader({ activeSection }: { activeSection?: 'calendar' | 'results' | 'appeal' }) {
  return (
    <header className="site-header">
      <a className="brand" href="/" aria-label="Спортрезультаты — каталог">
        <span className="brand-mark">SR</span>
        <span>Спортрезультаты</span>
      </a>
      <nav className="site-navigation" aria-label="Публичные разделы">
        <a href="/events" aria-current={activeSection === 'calendar' ? 'page' : undefined}>Мероприятия</a>
        <a href="/results" aria-current={activeSection === 'results' ? 'page' : undefined}>Результаты</a>
        <a href="/appeal" aria-current={activeSection === 'appeal' ? 'page' : undefined}>Обращение</a>
      </nav>
      <span className="header-note"><i /> Официальные протоколы</span>
    </header>
  )
}
