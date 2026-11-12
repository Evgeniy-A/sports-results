export function SiteHeader() {
  return (
    <header className="site-header">
      <a className="brand" href="/" aria-label="Лига Героев — спортивные результаты">
        <strong className="brand-organizer">Лига Героев</strong>
        <span className="brand-divider" aria-hidden="true" />
        <span className="brand-title">Спортивные результаты</span>
      </a>
      <span className="header-note"><i /> Официальные протоколы</span>
    </header>
  )
}
