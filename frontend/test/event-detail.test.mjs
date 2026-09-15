import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')
const detail = await readFile(new URL('../src/pages/EventDetailPage.tsx', import.meta.url), 'utf8')
const deepLink = await readFile(new URL('../src/pages/EventResultsPage.tsx', import.meta.url), 'utf8')
const page = await readFile(new URL('../src/pages/PublicEventPage.tsx', import.meta.url), 'utf8')
const info = await readFile(new URL('../src/components/EventInfo.tsx', import.meta.url), 'utf8')

test('event and legacy results routes reuse one smart public event page', () => {
  assert.match(app, /resultsMatch = window\.location\.pathname\.match/)
  assert.match(app, /\/results\\\/\?\$\/\)/)
  assert.match(app, /EventDetailPage/)
  assert.match(app, /EventResultsPage/)
  assert.match(detail, /<PublicEventPage slug=\{slug\}/)
  assert.match(deepLink, /<PublicEventPage slug=\{slug\}/)
})

test('results-first is controlled strictly by results publication status', () => {
  assert.match(page, /event\.resultsPublicationStatus === 'PUBLISHED'/)
  assert.match(page, /defaultPublicEventSection\(loadedEvent\.resultsPublicationStatus\)/)
  assert.doesNotMatch(page, /event\.resultsPublished|totalElements|results\.length/)
  assert.match(page, /resultsPublished && <div id="results-view"/)
})

test('published event exposes results and event-info tabs while retaining results state', () => {
  assert.match(page, /aria-selected=\{section === 'results'\}/)
  assert.match(page, /aria-selected=\{section === 'info'\}/)
  assert.match(page, /onClick=\{\(\) => setSection\('results'\)\}/)
  assert.match(page, /onClick=\{\(\) => setSection\('info'\)\}/)
  assert.match(page, /hidden=\{section !== 'results'\}[\s\S]*<EventResults key=\{event\.id\} event=\{event\}/)
})

test('draft event renders information and never mounts the public results component', () => {
  assert.match(info, /event\.resultsPublicationStatus === 'DRAFT'/)
  assert.match(info, /Результаты будут опубликованы после мероприятия/)
  assert.match(page, /resultsPublished && <div id="results-view"/)
})

test('event info retains structured rules and optional participant content', () => {
  assert.match(info, /rules\.rankingBasis/)
  assert.match(info, /rules\.categoryEnabled/)
  assert.match(info, /event\.schedule\.length > 0/)
  assert.match(info, /event\.documents\.length > 0/)
  assert.match(info, /event\.infoBlocks\.length > 0/)
  assert.match(info, /event\.races\.map/)
  assert.doesNotMatch(info, /event\.sportFormats|format\.races/)
  assert.match(info, /!race\.resultsPublished/)
  assert.match(info, /Результаты ещё не опубликованы/)
})
