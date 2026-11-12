import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { PUBLIC_CATALOGS, publicCatalogMode } from '../src/utils/publicCatalog.ts'

const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')
const catalog = await readFile(new URL('../src/pages/EventCatalogPage.tsx', import.meta.url), 'utf8')
const eventPage = await readFile(new URL('../src/pages/PublicEventPage.tsx', import.meta.url), 'utf8')
const header = await readFile(new URL('../src/components/SiteHeader.tsx', import.meta.url), 'utf8')
const footer = await readFile(new URL('../src/components/SiteFooter.tsx', import.meta.url), 'utf8')

test('root and results render one results-first catalog', () => {
  assert.match(app, /\/\^\\\/\$\/\.test\(window\.location\.pathname\) \|\| catalogMode === 'results'/)
  assert.match(app, /return <EventCatalogPage mode="results" \/>/)
  assert.doesNotMatch(app, /PublicLandingPage/)
  assert.equal(PUBLIC_CATALOGS.results.title, 'Результаты мероприятий')
  assert.equal(PUBLIC_CATALOGS.results.phase, 'PAST')
})

test('calendar routes replace their URL with results without deleting calendar configuration', () => {
  assert.match(app, /catalogMode === 'calendar'\) return <ResultsCatalogRedirect \/>/)
  assert.match(app, /window\.history\.replaceState\(null, '', '\/results'\)/)
  assert.equal(publicCatalogMode('/events'), 'calendar')
  assert.equal(publicCatalogMode('/calendar'), 'calendar')
  assert.equal(PUBLIC_CATALOGS.calendar.phase, 'UPCOMING')
})

test('results route remains explicit while event deep links are not treated as catalog routes', () => {
  assert.equal(publicCatalogMode('/results'), 'results')
  assert.equal(publicCatalogMode('/results/'), 'results')
  assert.equal(publicCatalogMode('/events/example'), null)
})

test('public header and footer keep a quiet text brand without public navigation', () => {
  assert.match(header, /className="brand-organizer">Лига Героев<\/strong>/)
  assert.match(header, /className="brand-title">Спортивные результаты<\/span>/)
  assert.match(footer, /className="brand-organizer">Лига Героев<\/strong>/)
  assert.match(footer, /className="brand-title">Спортивные результаты<\/span>/)
  assert.doesNotMatch(header + footer, /site-navigation|brand-mark|>SR/)
  assert.doesNotMatch(`${header}\n${footer}`, /href="\/(?:events|calendar)"|>Мероприятия</)
})

test('catalog hero keeps the results message without a prominent appeal card', () => {
  assert.doesNotMatch(catalog, /catalog-appeal-link|Не нашли свой результат/)
})

test('results catalog keeps filters and compact result deep links without event images', () => {
  assert.equal(PUBLIC_CATALOGS.results.phase, 'PAST')
  assert.equal(PUBLIC_CATALOGS.results.route, '/results')
  assert.match(catalog, /aria-label="Фильтры мероприятий"/)
  assert.match(catalog, /Год/)
  assert.match(catalog, /Серия/)
  assert.match(catalog, /Город/)
  assert.match(catalog, /Точная дата/)
  assert.match(catalog, /mode === 'results' \? `\/events\/\$\{event\.slug\}\/results`/)
  assert.match(catalog, /className="event-card-action">Результаты →/)
  assert.doesNotMatch(catalog, /<img|heroImage|previewImage|coverImage/)
})

test('existing public event and result deep links remain ahead of catalog routing', () => {
  assert.match(app, /resultsMatch = window\.location\.pathname\.match/)
  assert.match(app, /eventMatch = window\.location\.pathname\.match/)
  assert.match(app, /if \(resultsMatch\) return <EventResultsPage/)
  assert.match(app, /if \(eventMatch\) return <EventDetailPage/)
  assert.match(eventPage, /href="\/results">← Все результаты<\/a>/)
})
