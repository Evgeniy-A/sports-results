import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { linkifyHttpText } from '../src/utils/linkifyText.ts'

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

test('results-first is controlled by published Race results and supports partial publication', () => {
  assert.match(page, /event\.races\.some\(\(race\) => race\.resultsPublished\)/)
  assert.match(page, /defaultPublicEventSection\(loadedEvent\.races\.some/)
  assert.doesNotMatch(page, /event\.resultsPublicationStatus === 'PUBLISHED'|totalElements|results\.length/)
  assert.match(page, /resultsPublished && <div id="results-view"/)
})

test('published event exposes results and event-info tabs while retaining results state', () => {
  assert.match(page, /aria-selected=\{section === 'results'\}/)
  assert.match(page, /aria-selected=\{section === 'info'\}/)
  assert.match(page, /onClick=\{\(\) => setSection\('results'\)\}/)
  assert.match(page, /onClick=\{\(\) => setSection\('info'\)\}/)
  assert.match(page, /hidden=\{section !== 'results'\}[\s\S]*<EventResults key=\{event\.id\} event=\{event\}/)
  assert.match(page, /className="event-hero-content"/)
  assert.match(page, /<SiteHeader \/>/)
  assert.doesNotMatch(page, /<img|heroImage|previewImage|coverImage/)
})

test('all-draft Race set renders information and never mounts the public results component', () => {
  assert.match(info, /event\.races\.some\(\(race\) => race\.resultsPublished\)/)
  assert.match(info, /Результаты пока не опубликованы/)
  assert.doesNotMatch(info, /Результаты будут опубликованы после мероприятия/)
  assert.match(page, /resultsPublished && <div id="results-view"/)
})

test('event info retains structured rules and optional participant content', () => {
  assert.match(info, /rules\.rankingBasis/)
  assert.match(info, /rules\.categoryEnabled/)
  assert.match(info, /event\.schedule\.length > 0/)
  assert.match(info, /generalDocuments\.length > 0/)
  assert.match(info, /event\.infoBlocks\.length > 0/)
  assert.match(info, /event\.races\.map/)
  assert.doesNotMatch(info, /event\.sportFormats|format\.races/)
  assert.match(info, /!race\.resultsPublished/)
  assert.match(info, /Результаты ещё не опубликованы/)
})

test('participant information groups public documents by block and keeps legacy documents general', () => {
  assert.match(info, /document\.infoBlockId === null/)
  assert.match(info, /document\.infoBlockId === block\.id/)
  assert.match(info, /<DocumentLinks documents=\{generalDocuments\}/)
  assert.match(info, /<DocumentLinks documents=\{documentsByBlock\.get\(block\.id\) \?\? \[\]\}/)
  assert.match(info, /linkifyHttpText\(value\)/)
})

test('participant information linkifies only safe HTTP URLs without surrounding punctuation', () => {
  assert.deepEqual(
    linkifyHttpText('Читайте (https://example.com/path), затем javascript:alert(1).'),
    [
      { text: 'Читайте (' },
      { text: 'https://example.com/path', href: 'https://example.com/path' },
      { text: '),' },
      { text: ' затем javascript:alert(1).' },
    ],
  )
  assert.deepEqual(
    linkifyHttpText('Документация: https://example.com/a_(b).'),
    [
      { text: 'Документация: ' },
      { text: 'https://example.com/a_(b)', href: 'https://example.com/a_(b)' },
      { text: '.' },
    ],
  )
  assert.deepEqual(linkifyHttpText('Зеркало http://a.'), [
    { text: 'Зеркало ' },
    { text: 'http://a', href: 'http://a' },
    { text: '.' },
  ])
})

test('public rules distinguish primary and category scoring bases', () => {
  assert.match(info, /Основной зачёт/)
  assert.match(info, /rules\.categoryRankingBasis/)
  assert.match(info, /category\.rankingBasis/)
})

test('public event documents open the configured API resource in a separate safe tab', () => {
  assert.match(info, /href=\{apiResourceUrl\(document\.contentUrl\)\}/)
  assert.match(info, /target="_blank"/)
  assert.match(info, /rel="noopener noreferrer"/)
  assert.doesNotMatch(info, /document\.contentUrl[\s\S]{0,120}onClick=/)
})
