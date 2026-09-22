import assert from 'node:assert/strict'
import test from 'node:test'
import {
  defaultPublicResultSort,
  defaultPublicEventSection,
  initialRaceSelection,
  publicCategoryName,
  publicResultRaces,
} from '../src/utils/publicEventView.ts'

const race = (id, name, resultsPublished = true) => ({ id, name, resultsPublished })

test('any published race opens results-first and all-draft races open event-info-first', () => {
  assert.equal(defaultPublicEventSection(true), 'results')
  assert.equal(defaultPublicEventSection(false), 'info')
})

test('default result ordering follows the configured sport time without inventing NONE standing', () => {
  assert.equal(defaultPublicResultSort('GUN_TIME'), 'gunTime')
  assert.equal(defaultPublicResultSort('CHIP_TIME'), 'chipTime')
  assert.equal(defaultPublicResultSort('NONE'), 'gunTime')
})

test('one published start opens immediately without an extra selection', () => {
  assert.equal(initialRaceSelection([race(11, 'Масс-старт 10 км')]), '11')
})

test('multiple published starts require one explicit start selection', () => {
  assert.equal(initialRaceSelection([
    race(11, 'Масс-старт 10 км'),
    race(12, 'Чемпионат'),
  ]), '')
})

test('draft starts are excluded without changing the source event program', () => {
  const sourceRaces = [race(11, '10 км', true), race(12, '42.2 км', false)]
  const resultRaces = publicResultRaces(sourceRaces)

  assert.equal(sourceRaces.length, 2)
  assert.deepEqual(resultRaces, [race(11, '10 км', true)])
  assert.equal(initialRaceSelection(resultRaces), '11')
})

test('all-draft start set produces no public result selection', () => {
  const resultRaces = publicResultRaces([race(11, '10 км', false)])

  assert.deepEqual(resultRaces, [])
  assert.equal(initialRaceSelection(resultRaces), '')
})

test('public category requires both race policy and participant category data', () => {
  assert.equal(publicCategoryName(false, { name: '18+ М' }), null)
  assert.equal(publicCategoryName(true, { name: 'М 30–34' }), 'М 30–34')
  assert.equal(publicCategoryName(true, null), null)
})

test('category visibility remains race-scoped inside one event', () => {
  const races = [
    { id: 11, categoryEnabled: false, category: { name: '18+ М' } },
    { id: 12, categoryEnabled: true, category: { name: 'Ж 35–39' } },
  ]

  assert.deepEqual(
    races.map((candidate) => publicCategoryName(candidate.categoryEnabled, candidate.category)),
    [null, 'Ж 35–39'],
  )
})
