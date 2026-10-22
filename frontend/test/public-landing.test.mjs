import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { PUBLIC_CATALOGS, publicCatalogMode } from '../src/utils/publicCatalog.ts'

const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')
const landing = await readFile(new URL('../src/pages/PublicLandingPage.tsx', import.meta.url), 'utf8')
const catalog = await readFile(new URL('../src/pages/EventCatalogPage.tsx', import.meta.url), 'utf8')
const eventPage = await readFile(new URL('../src/pages/PublicEventPage.tsx', import.meta.url), 'utf8')

test('root renders a dedicated landing instead of the mixed event catalog', () => {
  assert.match(app, /if \(\/\^\\\/\$\/\.test\(window\.location\.pathname\)\) return <PublicLandingPage/)
  assert.match(landing, /Старты и результаты/)
  assert.match(landing, /Официальные результаты/)
  assert.match(landing, /Календарь стартов/)
  assert.doesNotMatch(landing, /Что вы хотите посмотреть\?/)
  assert.doesNotMatch(landing, /EventCatalogPage/)
})

test('landing result and calendar cards link to separate sections', () => {
  assert.match(landing, /className="landing-choice landing-choice-results" href="\/results"/)
  assert.match(landing, /Смотреть результаты/)
  assert.match(landing, /className="landing-choice landing-choice-calendar" href="\/events"/)
  assert.match(landing, /Смотреть календарь/)
})

test('catalog route selection keeps calendar and result sections distinct', () => {
  assert.equal(publicCatalogMode('/events'), 'calendar')
  assert.equal(publicCatalogMode('/events/'), 'calendar')
  assert.equal(publicCatalogMode('/calendar'), 'calendar')
  assert.equal(publicCatalogMode('/results'), 'results')
  assert.equal(publicCatalogMode('/results/'), 'results')
  assert.equal(publicCatalogMode('/events/example'), null)
})

test('calendar always queries upcoming events and never the past archive', () => {
  assert.equal(PUBLIC_CATALOGS.calendar.phase, 'UPCOMING')
  assert.equal(PUBLIC_CATALOGS.calendar.route, '/events')
  assert.notEqual(PUBLIC_CATALOGS.calendar.phase, 'PAST')
  assert.match(catalog, /phase: catalog\.phase/)
  assert.doesNotMatch(catalog, /aria-label="Период"/)
})

test('results always query past events and link cards to result deep links', () => {
  assert.equal(PUBLIC_CATALOGS.results.phase, 'PAST')
  assert.equal(PUBLIC_CATALOGS.results.route, '/results')
  assert.notEqual(PUBLIC_CATALOGS.results.phase, 'UPCOMING')
  assert.match(catalog, /mode === 'results' \? `\/events\/\$\{event\.slug\}\/results`/)
})

test('existing public event and result deep links remain ahead of catalog routing', () => {
  assert.match(app, /resultsMatch = window\.location\.pathname\.match/)
  assert.match(app, /eventMatch = window\.location\.pathname\.match/)
  assert.match(app, /if \(resultsMatch\) return <EventResultsPage/)
  assert.match(app, /if \(eventMatch\) return <EventDetailPage/)
  assert.match(eventPage, /href="\/">← На главную<\/a>/)
})
