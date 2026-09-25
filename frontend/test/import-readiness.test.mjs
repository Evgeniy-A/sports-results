import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

import { calculateImportReadiness } from '../src/admin/importReadiness.ts'

const importTab = readFileSync(new URL('../src/admin/pages/EventImportTab.tsx', import.meta.url), 'utf8')

function analysis(overrides = {}) {
  return {
    filename: 'results.csv',
    fileType: 'CSV',
    sportsResultsTemplate: false,
    legacyCsv: true,
    templateFormatVersion: null,
    metadataEventId: null,
    headerSignature: 'synthetic',
    sheets: [{ name: 'CSV', rowCount: 4115, raceId: null }],
    columns: [],
    columnMappings: {},
    canonicalFields: [
      { field: 'BIB', displayName: 'Стартовый номер', required: true },
      { field: 'STATUS', displayName: 'Статус', required: true },
      { field: 'RACE', displayName: 'Старт', required: false },
      { field: 'GUN_TIME', displayName: 'Официальное время', required: false },
    ],
    missingRequiredFields: [],
    raceDiscriminatorHeader: 'event',
    raceValues: [
      { sourceValue: '10 km', raceId: 1, raceName: '10 км', automatic: true },
      { sourceValue: '42.2 km', raceId: 2, raceName: '42,2 км', automatic: true },
    ],
    resolvedRaceIds: [1, 2],
    suggestedProfile: null,
    diagnostics: [],
    readyForValidation: true,
    ...overrides,
  }
}

const mappings = {
  dorsal: 'BIB',
  status: 'STATUS',
  event: 'RACE',
  'times.official_:::finish:::': 'GUN_TIME',
}

function readiness(overrides = {}) {
  return calculateImportReadiness({
    analysis: analysis(),
    sourceKind: 'EXTERNAL',
    externalScope: 'MULTI',
    targetRaceId: null,
    columnMappings: mappings,
    raceMappings: { '10 km': 1, '42.2 km': 2 },
    ...overrides,
  })
}

test('compatible CSV with required columns and two auto-matched starts can be validated', () => {
  const result = readiness()
  assert.equal(result.canValidate, true)
  assert.deepEqual(result.scopeRaceIds, [1, 2])
  assert.equal(result.mappedRaceCount, 2)
  assert.equal(result.importedFieldCount, 4)
})

test('unmatched starts expose mapping controls and a concrete instruction', () => {
  const result = readiness({ raceMappings: {} })
  assert.equal(result.canValidate, false)
  assert.match(result.problems.join(' '), /Сопоставьте все 2 старта/)
  assert.match(importTab, /Сопоставьте найденные в файле дистанции со стартами мероприятия/)
  assert.match(importTab, /aria-label=\{`Старт для \$\{value\.sourceValue\}`\}/)
})

test('manual mapping of all starts enables validation', () => {
  const source = analysis({
    readyForValidation: false,
    raceValues: [
      { sourceValue: '10 km', raceId: null, raceName: null, automatic: false },
      { sourceValue: '42.2 km', raceId: null, raceName: null, automatic: false },
    ],
    resolvedRaceIds: [],
  })
  const result = readiness({ analysis: source, raceMappings: { '10 km': 1, '42.2 km': 2 } })
  assert.equal(result.canValidate, true)
  assert.deepEqual(result.scopeRaceIds, [1, 2])
})

test('missing required fields keep validation disabled and explain why', () => {
  const result = readiness({ columnMappings: { event: 'RACE' } })
  assert.equal(result.canValidate, false)
  assert.match(result.problems.join(' '), /Стартовый номер/)
  assert.match(result.problems.join(' '), /Статус/)
})

test('ambiguous start is never selected automatically and accepts a manual choice', () => {
  const source = analysis({
    readyForValidation: false,
    raceValues: [{ sourceValue: '10 km', raceId: null, raceName: null, automatic: false }],
    resolvedRaceIds: [],
  })
  const blocked = readiness({ analysis: source, raceMappings: {} })
  assert.equal(blocked.canValidate, false)
  assert.match(blocked.problems.join(' '), /10 km/)

  const selected = readiness({ analysis: source, raceMappings: { '10 km': 2 } })
  assert.equal(selected.canValidate, true)
  assert.deepEqual(selected.scopeRaceIds, [2])
})
