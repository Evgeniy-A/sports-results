import type { EventDetails, RaceRules } from '../api/types'
import { formatLocalScheduleTime } from '../utils/format'

const rankingBasis = { GUN_TIME: 'по времени от стартового сигнала', CHIP_TIME: 'по чистому времени участника', NONE: 'не проводится' }
const primaryMode = { ALL: 'единый общий зачёт', BY_GENDER: 'мужчины и женщины отдельно', NONE: 'не проводится' }

function Rules({ rules }: { rules: RaceRules | null }) {
  if (!rules) return <p className="muted-copy">Правила официального зачёта ещё не настроены.</p>
  return <dl className="rules-grid">
    <div><dt>Официальный зачёт</dt><dd>{rankingBasis[rules.rankingBasis]}</dd></div>
    <div><dt>Основной зачёт</dt><dd>{primaryMode[rules.primaryStandingMode]}</dd></div>
    <div><dt>Призовых мест</dt><dd>{rules.absolutePrizePlaces || 'не предусмотрено'}</dd></div>
    <div><dt>Возрастной зачёт</dt><dd>{rules.categoryEnabled ? 'проводится' : 'не проводится'}</dd></div>
    {rules.categoryEnabled && <>
      <div><dt>Возраст определяется</dt><dd>{rules.ageCalculationMode === 'END_OF_EVENT_YEAR' ? 'на 31 декабря года мероприятия' : 'на дату мероприятия'}</dd></div>
      <div><dt>Призовых мест в категории</dt><dd>{rules.categoryPrizePlaces}</dd></div>
      {rules.categories.length > 0 && <div className="rules-wide"><dt>Категории</dt><dd>{rules.categories.map((category) => category.name).join(' · ')}</dd></div>}
      {rules.excludeAbsoluteWinnersFromCategory && <div className="rules-wide"><dt>Исключение призёров</dt><dd>Призёры основного зачёта исключаются из возрастного зачёта.</dd></div>}
    </>}
  </dl>
}

export function EventInfo({ event }: { event: EventDetails }) {
  const info = event.participantInfo

  return <div className="event-detail-layout">
    {event.resultsPublicationStatus === 'DRAFT' && <div className="results-awaiting-card" role="status">
      <strong>Результаты будут опубликованы после мероприятия</strong>
      <span>Сейчас доступны программа, место проведения и правила стартов.</span>
    </div>}

    {(info?.venueName || info?.venueAddress || info?.locationDescription) && <section className="detail-section">
      <p className="eyebrow">Место проведения</p><h2>{info.venueName ?? event.location}</h2>
      {info.venueAddress && <p className="detail-address">{info.venueAddress}</p>}
      {info.locationDescription && <p>{info.locationDescription}</p>}
    </section>}

    {event.schedule.length > 0 && <section className="detail-section">
      <p className="eyebrow">Программа</p><h2>Расписание</h2>
      <div className="schedule-list">{event.schedule.map((item) => <article key={item.id}>
        <time>{formatLocalScheduleTime(item.startsAt)}{item.endsAt ? ` — ${item.endsAt.slice(11, 16)}` : ''}</time>
        <div><h3>{item.title}</h3>{item.description && <p>{item.description}</p>}</div>
      </article>)}</div>
    </section>}

    {event.races.length > 0 && <section className="detail-section">
      <p className="eyebrow">Спортивная программа</p><h2>Старты и правила</h2>
      <div className="race-rule-list">{event.races.map((race) => <article className="race-rule-card" key={race.id}>
          <h3>{race.name}</h3>{!race.resultsPublished && <p className="muted-copy">Результаты ещё не опубликованы.</p>}
          {race.clusters.length > 0 && <div className="cluster-list"><strong>Стартовые волны:</strong>{race.clusters.map((cluster) => <span key={cluster.id}>{cluster.displayName}{cluster.startsAt ? ` · ${formatLocalScheduleTime(cluster.startsAt)}` : ''}</span>)}</div>}
          <Rules rules={race.rules} />
        </article>)}</div>
    </section>}

    {event.documents.length > 0 && <section className="detail-section">
      <p className="eyebrow">Материалы</p><h2>Документы</h2>
      <div className="document-list">{event.documents.map((document) => <a href={document.contentUrl} target="_blank" rel="noreferrer" key={document.id}><span>PDF</span><strong>{document.displayName}</strong><i>Открыть ↗</i></a>)}</div>
    </section>}

    {event.infoBlocks.length > 0 && <section className="detail-section">
      <p className="eyebrow">Участникам</p><h2>Важная информация</h2>
      <div className="info-block-grid">{event.infoBlocks.map((block) => <article key={block.id}><h3>{block.title}</h3><p>{block.content}</p></article>)}</div>
    </section>}
    {info?.additionalInfo && <section className="detail-section compact-section"><p>{info.additionalInfo}</p></section>}
  </div>
}
