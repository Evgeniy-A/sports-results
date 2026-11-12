import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { PUBLIC_CATALOGS, publicCatalogMode } from '../src/utils/publicCatalog.ts'

const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')
const catalog = await readFile(new URL('../src/pages/EventCatalogPage.tsx', import.meta.url), 'utf8')
const eventPage = await readFile(new URL('../src/pages/PublicEventPage.tsx', import.meta.url), 'utf8')
const header = await readFile(new URL('../src/components/SiteHeader.tsx', import.meta.url), 'utf8')
const footer = await readFile(new URL('../src/components/SiteFooter.tsx', import.meta.url), 'utf8')
const brandMark = await readFile(new URL('../src/components/PublicBrandMark.tsx', import.meta.url), 'utf8')
const publicCss = await readFile(new URL('../src/public.css', import.meta.url), 'utf8')

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

test('public header stays menu-free while footer retains quiet results and appeal access', () => {
  assert.doesNotMatch(header, /<nav|href="\/(?:results|appeal)"/)
  assert.match(header, /<PublicBrandMark \/>/)
  assert.match(header, /Спортивные результаты/)
  assert.match(header, /Официальные протоколы/)
  assert.match(footer, /href="\/results">Результаты<\/a>/)
  assert.match(footer, /href="\/appeal">Обращение<\/a>/)
  assert.doesNotMatch(`${header}\n${footer}`, /href="\/(?:events|calendar)"|>Мероприятия</)
})

test('public brand is an independent replaceable placeholder and presentation uses a soft navy violet gradient', () => {
  assert.match(brandMark, /<svg className="public-brand-mark"/)
  assert.match(brandMark, /viewBox="0 0 64 36"/)
  assert.doesNotMatch(brandMark, /img|Лига Героев|https?:\/\//)
  assert.match(publicCss, /radial-gradient\(circle at 18% 18%, rgba\(220, 25, 65, \.16\)/)
  assert.match(publicCss, /linear-gradient\(110deg, #07101f 0%, #101127 62%, #160d2d 100%\)/)
  assert.doesNotMatch(publicCss, /#dfff42|rgba\(223,\s*255,\s*66/)
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
  assert.doesNotMatch(catalog, /catalog-appeal-link|Не нашли свой результат/)
  assert.doesNotMatch(catalog, /<img|heroImage|previewImage|coverImage/)
})

test('existing public event and result deep links remain ahead of catalog routing', () => {
  assert.match(app, /resultsMatch = window\.location\.pathname\.match/)
  assert.match(app, /eventMatch = window\.location\.pathname\.match/)
  assert.match(app, /if \(resultsMatch\) return <EventResultsPage/)
  assert.match(app, /if \(eventMatch\) return <EventDetailPage/)
  assert.match(eventPage, /href="\/results">← Все результаты<\/a>/)
})
