import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client'
import type { EventDetails, PageResponse, ResultInquiryLookup, ResultListItem } from '../api/types'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { formatDuration, statusLabel } from '../utils/format'
import { defaultPublicResultSort, initialRaceSelection, publicResultRaces } from '../utils/publicEventView'
import { scoringTimeHeading } from '../utils/rankingPresentation'
import {
  LatestRequestGate,
  isRequestCancellation,
  loadPublicResultsAndInquiry,
  resolvePublicResultsAndInquiry,
  shouldRequestResultInquiry,
} from '../utils/resultInquiry'
import { Pagination } from './Pagination'
import { RankingAchievements } from './RankingAchievements'
import { ResultDetailsDialog } from './ResultDetailsDialog'
import { ResultInquiryPanel } from './ResultInquiryPanel'

const EMPTY_PAGE: PageResponse<ResultListItem> = {
  content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: 'place', direction: 'asc',
}

export function EventResults({ event }: { event: EventDetails }) {
  const initialRaces = publicResultRaces(event.races)
  const initialRaceId = initialRaceSelection(initialRaces)
  const initialRace = initialRaces.find((race) => String(race.id) === initialRaceId)
  const [protocol, setProtocol] = useState<PageResponse<ResultListItem>>(EMPTY_PAGE)
  const [raceId, setRaceId] = useState(initialRaceId)
  const [gender, setGender] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [clusterId, setClusterId] = useState('')
  const [status, setStatus] = useState('')
  const [name, setName] = useState('')
  const [bib, setBib] = useState('')
  const [sort, setSort] = useState<string>(defaultPublicResultSort(initialRace?.rules?.rankingBasis))
  const [direction, setDirection] = useState<'asc' | 'desc'>('asc')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(Boolean(initialRaceId))
  const [error, setError] = useState<string | null>(null)
  const [inquiryError, setInquiryError] = useState<string | null>(null)
  const [selectedResultId, setSelectedResultId] = useState<number | null>(null)
  const [inquiry, setInquiry] = useState<ResultInquiryLookup | null>(null)
  const [inquiryLoading, setInquiryLoading] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [verificationError, setVerificationError] = useState<string | null>(null)
  const [verifiedInquiryBirthDate, setVerifiedInquiryBirthDate] = useState<string | null>(null)
  const protocolRequestRef = useRef<AbortController | null>(null)
  const verificationRequestRef = useRef<AbortController | null>(null)
  const [protocolRequestGate] = useState(() => new LatestRequestGate())
  const debouncedName = useDebouncedValue(name, 350)
  const debouncedBib = useDebouncedValue(bib, 350)
  const races = useMemo(() => publicResultRaces(event.races), [event.races])
  const selectedRace = useMemo(() => races.find((race) => String(race.id) === raceId), [races, raceId])
  const categories = selectedRace?.rules?.categoryEnabled
    ? selectedRace.rules.categories.map((category) => ({ id: category.id, name: category.name }))
    : []
  const showCategoryColumn = selectedRace?.rules?.categoryEnabled ?? false
  const rankingBasis = selectedRace?.rules?.rankingBasis ?? protocol.content[0]?.rankingBasis
  const hasOfficialStanding = rankingBasis !== undefined && rankingBasis !== 'NONE'
  const hasPublicResultRows = protocol.content.length > 0
  const rankingMode = categoryId ? 'CATEGORY' : 'PRIMARY'

  const clearInquiryState = () => {
    protocolRequestGate.invalidate()
    protocolRequestRef.current?.abort()
    verificationRequestRef.current?.abort()
    setInquiry(null)
    setInquiryError(null)
    setInquiryLoading(false)
    setVerifying(false)
    setVerificationError(null)
    setVerifiedInquiryBirthDate(null)
  }

  const selectPublicResultRace = useCallback((lookup: ResultInquiryLookup) => {
    if (!lookup.raceId) return
    if (String(lookup.raceId) === raceId) return
    const targetRace = races.find((race) => race.id === lookup.raceId)
    if (!targetRace) return
    setRaceId(String(targetRace.id))
    setProtocol(EMPTY_PAGE)
    setCategoryId('')
    setClusterId('')
    setStatus('')
    setName('')
    setSort(defaultPublicResultSort(targetRace.rules?.rankingBasis))
    setDirection('asc')
    setPage(0)
  }, [
    races,
    raceId,
    setCategoryId,
    setClusterId,
    setDirection,
    setName,
    setPage,
    setProtocol,
    setRaceId,
    setSort,
    setStatus,
  ])

  useEffect(() => {
    protocolRequestGate.invalidate()
    protocolRequestRef.current?.abort()
    verificationRequestRef.current?.abort()
    // oxlint-disable-next-line react/set-state-in-effect -- an Event change invalidates all personal lookup state.
    setInquiry(null)
    setInquiryError(null)
    setVerificationError(null)
    setVerifying(false)
    setVerifiedInquiryBirthDate(null)
    setBib('')
  }, [event.id, protocolRequestGate])

  useEffect(() => {
    if (!raceId) return
    const requestId = protocolRequestGate.begin()
    const controller = new AbortController()
    protocolRequestRef.current = controller
    const isCurrentRequest = () => protocolRequestGate.isCurrent(requestId)
      && protocolRequestRef.current === controller
      && !controller.signal.aborted
    // oxlint-disable-next-line react/set-state-in-effect -- loading starts with the external request.
    setLoading(true)
    setError(null)
    setInquiry(null)
    setInquiryError(null)
    setInquiryLoading(false)
    setVerificationError(null)
    setVerifiedInquiryBirthDate(null)
    const filters = {
      name: debouncedName || undefined,
      bib: debouncedBib.trim() || undefined,
      gender: gender || undefined,
      categoryId: categoryId ? Number(categoryId) : undefined,
      clusterId: clusterId ? Number(clusterId) : undefined,
      status: status || undefined,
    }
    const shouldLoadInquiry = shouldRequestResultInquiry(filters)
    setInquiryLoading(shouldLoadInquiry)
    loadPublicResultsAndInquiry(
      filters,
      () => api.results(event.id, {
        raceId: Number(raceId),
        ...filters,
        sort,
        direction,
        page,
        size: 50,
      }, controller.signal),
      () => api.resultInquiry(event.id, debouncedBib.trim(), controller.signal),
    ).then((outcome) => {
      if (!isCurrentRequest()) return
      const resolution = resolvePublicResultsAndInquiry(outcome)
      if (resolution.state === 'CANCELLED') return
      if (resolution.state === 'PUBLIC_RESULTS_FAILED') {
        console.error('Protocol request failed', resolution.error)
        setError('Не удалось получить протокол. Попробуйте ещё раз.')
        setInquiry(null)
        return
      }
      setProtocol(resolution.publicResults)
      if (resolution.inquiryError) {
        console.error('Result inquiry request failed', resolution.inquiryError)
        setInquiryError('Результаты загружены, но сейчас не удалось проверить возможность уточнения. Попробуйте повторить поиск.')
      }
      const lookup = resolution.inquiry
      if (!lookup) return
      if (lookup.lookupState === 'RESULT_PUBLIC') {
        selectPublicResultRace(lookup)
        setInquiry(null)
      } else {
        setInquiry(lookup)
      }
    }).catch((reason: unknown) => {
      if (isCurrentRequest() && !isRequestCancellation(reason)) {
        console.error('Protocol request failed', reason)
        setError('Не удалось получить протокол. Попробуйте ещё раз.')
      }
    }).finally(() => {
      if (isCurrentRequest()) {
        setLoading(false)
        setInquiryLoading(false)
      }
    })
    return () => {
      controller.abort()
      if (protocolRequestRef.current === controller) protocolRequestRef.current = null
    }
  }, [event.id, raceId, debouncedName, debouncedBib, gender, categoryId, clusterId, status, sort, direction, page, protocolRequestGate, selectPublicResultRace])

  const verifyBirthDate = async (birthDate: string) => {
    if (!inquiry) return
    verificationRequestRef.current?.abort()
    const controller = new AbortController()
    verificationRequestRef.current = controller
    setVerifying(true)
    setVerificationError(null)
    try {
      const verified = await api.verifyResultInquiry(
        event.id,
        { bib: inquiry.bib, birthDate },
        controller.signal,
      )
      if (controller.signal.aborted) return
      if (verified.lookupState === 'RESULT_PUBLIC') {
        selectPublicResultRace(verified)
        setInquiry(null)
      } else {
        setVerifiedInquiryBirthDate(
          verified.lookupState === 'RESULT_NOT_PUBLIC' ? birthDate : null,
        )
        setInquiry(verified)
      }
    } catch (reason: unknown) {
      if (!controller.signal.aborted) {
        console.error('Result inquiry verification failed', reason)
        setVerificationError('Не удалось выполнить проверку. Попробуйте ещё раз.')
      }
    } finally {
      if (!controller.signal.aborted) setVerifying(false)
      if (verificationRequestRef.current === controller) verificationRequestRef.current = null
    }
  }

  const resetPage = () => setPage(0)
  const changeRace = (nextRaceId: string) => {
    clearInquiryState()
    const nextRace = races.find((candidate) => String(candidate.id) === nextRaceId)
    setRaceId(nextRaceId)
    setProtocol(EMPTY_PAGE)
    setCategoryId('')
    setClusterId('')
    setSort(defaultPublicResultSort(nextRace?.rules?.rankingBasis))
    setDirection('asc')
    resetPage()
  }
  const resetFilters = () => {
    clearInquiryState()
    setGender('')
    setCategoryId('')
    setClusterId('')
    setStatus('')
    setName('')
    setBib('')
    setPage(0)
  }

  return <section className="results-panel" aria-labelledby="protocol-title">
    <div className="section-heading protocol-heading">
      <div><p className="eyebrow">{hasOfficialStanding ? 'Официальные данные' : 'Результаты'}</p><h2 id="protocol-title">{hasOfficialStanding ? 'Протокол' : 'Результаты'}</h2></div>
      {selectedRace && <span className="result-count">Найдено: {(inquiry?.lookupState === 'RESULT_NOT_PUBLIC' ? 1 : protocol.totalElements).toLocaleString('ru-RU')}</span>}
    </div>

    <section className="protocol-selection" aria-label={hasOfficialStanding ? 'Выбор официального протокола' : 'Выбор таблицы результатов'}>
      {races.length > 1 && <div className="selection-group">
        <span>Старт</span>
        <div className="selection-buttons" role="group" aria-label="Старт">
          {races.map((race) => <button type="button" key={race.id} aria-pressed={String(race.id) === raceId} onClick={() => changeRace(String(race.id))}>{race.name}</button>)}
        </div>
      </div>}
      {selectedRace && <p><span>{hasOfficialStanding ? 'Официальный протокол' : 'Таблица результатов'}</span><strong>{selectedRace.name}</strong></p>}
    </section>

    {selectedRace && <div className="protocol-controls">
      <section className="control-section" aria-labelledby="filters-title">
        <div className="control-heading"><h3 id="filters-title">Фильтры</h3><button className="filter-reset" type="button" onClick={resetFilters}>Сбросить фильтры</button></div>
        <div className="result-filters" aria-label={hasOfficialStanding ? 'Фильтры протокола' : 'Фильтры результатов'}>
          {selectedRace.clusters.length >= 2 && <label><span>Стартовая волна</span><select aria-label="Стартовая волна" value={clusterId} onChange={(changeEvent) => { clearInquiryState(); setClusterId(changeEvent.target.value); resetPage() }}><option value="">Все стартовые волны</option>{selectedRace.clusters.map((cluster) => <option key={cluster.id} value={cluster.id}>{cluster.displayName}</option>)}</select></label>}
          <label><span>ФИО</span><input aria-label="Поиск по ФИО" value={name} onChange={(changeEvent) => { clearInquiryState(); setName(changeEvent.target.value); resetPage() }} placeholder="Начните вводить имя" /></label>
          <label><span>Стартовый номер</span><input aria-label="Точный стартовый номер" value={bib} onChange={(changeEvent) => { clearInquiryState(); setBib(changeEvent.target.value); resetPage() }} placeholder="Например, 00123" /></label>
          <label><span>Пол</span><select aria-label="Пол" value={gender} onChange={(changeEvent) => { clearInquiryState(); setGender(changeEvent.target.value); resetPage() }}><option value="">Все</option><option value="female">Женщины</option><option value="male">Мужчины</option></select></label>
          {selectedRace.rules?.categoryEnabled && <label><span>Категория</span><select aria-label="Категория" value={categoryId} onChange={(changeEvent) => { clearInquiryState(); setCategoryId(changeEvent.target.value); resetPage() }}><option value="">Все категории</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>}
          <label><span>Статус</span><select aria-label="Статус" value={status} onChange={(changeEvent) => { clearInquiryState(); setStatus(changeEvent.target.value); resetPage() }}><option value="">Финишировавшие и дисквалифицированные</option><option value="finished">Финишировавшие</option><option value="disqualified">Дисквалифицированные</option></select></label>
        </div>
      </section>
      <section className="control-section sorting-section" aria-labelledby="sorting-title">
        <h3 id="sorting-title">Сортировка</h3>
        <div className="sorting-controls">
          <label><span>Поле</span><select aria-label="Сортировка" value={sort} onChange={(changeEvent) => { clearInquiryState(); setSort(changeEvent.target.value); resetPage() }}><option value="gunTime">Официальное время{rankingBasis === 'GUN_TIME' ? ' · Зачёт' : ''}</option><option value="chipTime">Чистое время{rankingBasis === 'CHIP_TIME' ? ' · Зачёт' : ''}</option><option value="displayName">ФИО</option><option value="bib">Стартовый номер</option></select></label>
          <button className="direction-button" type="button" onClick={() => { clearInquiryState(); setDirection(direction === 'asc' ? 'desc' : 'asc'); resetPage() }}>{direction === 'asc' ? 'По возрастанию ↑' : 'По убыванию ↓'}</button>
        </div>
      </section>
    </div>}

    {selectedRace && (hasOfficialStanding
      ? <div className="context-note"><strong>Официальный зачёт</strong> рассчитывается только внутри выбранного старта и не меняется от фильтров или сортировки. Выбранная сортировка управляет только порядком строк.</div>
      : <div className="context-note"><strong>Место</strong> — позиция в текущей таблице результатов. Она меняется вместе с фильтрами и сортировкой и не является официальным спортивным местом.</div>)}
    {error && <div className="state-message error" role="alert">{error}</div>}
    {!error && inquiryError && <div className="state-message" role="status">{inquiryError}</div>}
    {!error && !selectedRace && !loading && <div className="state-message">{races.length === 0 ? 'Результаты ещё не опубликованы.' : 'Выберите старт.'}</div>}
    {selectedRace && loading && <div className="state-message" aria-live="polite">{inquiryLoading ? 'Проверяем стартовый номер…' : hasOfficialStanding ? 'Загружаем протокол…' : 'Загружаем результаты…'}</div>}
    {selectedRace && !loading && !error && protocol.content.length === 0 && !inquiry && <div className="state-message">По выбранным условиям результатов нет.</div>}
    {selectedRace && !loading && !error && inquiry && !hasPublicResultRows && <ResultInquiryPanel
      key={`${event.id}:${inquiry.bib}:${inquiry.lookupState}`}
      eventId={event.id}
      eventName={event.name}
      eventTimeZone={event.timeZone}
      inquiry={inquiry}
      verifiedBirthDate={verifiedInquiryBirthDate}
      verifying={verifying}
      verificationError={verificationError}
      onVerify={verifyBirthDate}
      onClearVerification={() => setVerifiedInquiryBirthDate(null)}
    />}

    {selectedRace && !loading && !error && protocol.content.length > 0 && <>
      <div className="table-wrap desktop-results"><table><thead><tr><th>{hasOfficialStanding ? 'Официальный зачёт' : 'Место'}</th><th>Стартовый №</th><th>Участник</th><th>Старт / дистанция</th><th>Статус</th><th><span className="time-heading">{scoringTimeHeading(rankingBasis ?? 'NONE', 'GUN_TIME')}</span></th><th><span className="time-heading">{scoringTimeHeading(rankingBasis ?? 'NONE', 'CHIP_TIME')}</span></th>{showCategoryColumn && <th>Категория</th>}</tr></thead><tbody>{protocol.content.map((result) => (
        <tr key={result.resultId} tabIndex={0} onClick={() => setSelectedResultId(result.resultId)} onKeyDown={(keyboardEvent) => { if (keyboardEvent.key === 'Enter') setSelectedResultId(result.resultId) }}>
          <td className={hasOfficialStanding ? 'ranking-cell' : 'display-place-cell'}>{hasOfficialStanding ? <RankingAchievements achievements={result.rankingAchievements} mode={rankingMode} /> : <span className="display-place">{result.displayPosition ?? '—'}</span>}</td><td><span className="bib">{result.bib ?? '—'}</span></td><td className="participant">{result.displayName}</td><td>{selectedRace.name}</td><td><span className={`status status-${result.status}`}>{statusLabel(result.status)}</span></td><td className={`time ${result.rankingBasis === 'GUN_TIME' ? 'time-primary' : ''}`}>{formatDuration(result.gunTimeMs)}</td><td className={`time ${result.rankingBasis === 'CHIP_TIME' ? 'time-primary' : ''}`}>{formatDuration(result.chipTimeMs)}</td>{showCategoryColumn && <td>{result.category?.name ?? '—'}</td>}
        </tr>
      ))}</tbody></table></div>
      <div className="mobile-results">{protocol.content.map((result) => <button className="result-card" type="button" key={result.resultId} onClick={() => setSelectedResultId(result.resultId)}><span><strong>{result.displayName}</strong><small>№ {result.bib ?? '—'} · {selectedRace.name}{showCategoryColumn && result.category ? ` · ${result.category.name}` : ''}</small></span><span className={`time ${hasOfficialStanding ? 'time-primary' : ''}`}>{formatDuration(result.rankingBasis === 'CHIP_TIME' || (result.rankingBasis === 'NONE' && sort === 'chipTime') ? result.chipTimeMs : result.gunTimeMs)}</span><span className="card-achievements">{hasOfficialStanding ? <RankingAchievements achievements={result.rankingAchievements} mode={rankingMode} /> : <span className="display-place">Место {result.displayPosition ?? '—'}</span>}</span></button>)}</div>
    </>}

    {selectedRace && <Pagination page={protocol.page} totalPages={protocol.totalPages} onChange={setPage} />}
    {selectedResultId !== null && selectedRace && <ResultDetailsDialog
      resultId={selectedResultId}
      eventName={event.name}
      eventTimeZone={event.timeZone}
      startName={selectedRace.name}
      categoryEnabled={showCategoryColumn}
      rankingMode={rankingMode}
      onClose={() => setSelectedResultId(null)}
    />}
  </section>
}
