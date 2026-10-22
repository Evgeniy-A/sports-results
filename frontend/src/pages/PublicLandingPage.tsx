import { SiteHeader } from '../components/SiteHeader'

export function PublicLandingPage() {
  return <main>
    <SiteHeader />
    <section className="landing-shell">
      <div className="landing-hero">
        <p className="eyebrow">Спортрезультаты</p>
        <h1>Старты и результаты</h1>
        <p>Выберите раздел: календарь предстоящих мероприятий или официальные результаты завершённых стартов.</p>
      </div>

      <div className="landing-choices" aria-label="Разделы сайта">
        <a className="landing-choice landing-choice-results" href="/results">
          <span className="landing-choice-icon" aria-hidden="true">
            <svg viewBox="0 0 48 48"><path d="M16 9h16v8c0 8-3 13-8 16-5-3-8-8-8-16V9Z" /><path d="M16 13H9c0 7 3 11 9 12M32 13h7c0 7-3 11-9 12M24 33v6M17 40h14" /></svg>
          </span>
          <span className="landing-choice-copy">
            <small>Протоколы</small>
            <strong>Официальные результаты</strong>
            <span>Официальные протоколы, поиск участника и результаты завершённых стартов.</span>
          </span>
          <span className="landing-choice-action">Смотреть результаты <i aria-hidden="true">→</i></span>
        </a>

        <a className="landing-choice landing-choice-calendar" href="/events">
          <span className="landing-choice-icon" aria-hidden="true">
            <svg viewBox="0 0 48 48"><path d="M10 14h28v25H10zM10 21h28M17 9v10M31 9v10" /><path d="M17 28h5M27 28h5M17 34h5M27 34h5" /></svg>
          </span>
          <span className="landing-choice-copy">
            <small>Календарь</small>
            <strong>Календарь стартов</strong>
            <span>Предстоящие мероприятия, даты, информация для участников и документы старта.</span>
          </span>
          <span className="landing-choice-action">Смотреть календарь <i aria-hidden="true">→</i></span>
        </a>
      </div>

      <div className="landing-support" aria-label="Возможности сервиса">
        <span>Официальные протоколы</span>
        <span>Поиск по имени и номеру</span>
        <span>Информация участникам</span>
      </div>
    </section>
  </main>
}
