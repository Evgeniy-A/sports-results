export function SiteHeader({ activeSection }: { activeSection?: 'calendar' | 'results' }) {
  return (
    <header className="site-header">
      <a className="brand" href="/" aria-label="Спортрезультаты — каталог">
        <span className="brand-mark">SR</span>
        <span>Спортрезультаты</span>
      </a>
      <nav className="site-navigation" aria-label="Публичные разделы">
        <a href="/events" aria-current={activeSection === 'calendar' ? 'page' : undefined}>Календарь</a>
        <a href="/results" aria-current={activeSection === 'results' ? 'page' : undefined}>Результаты</a>
      </nav>
      <span className="header-note"><i /> Проверенные протоколы</span>
    </header>
  )
}
