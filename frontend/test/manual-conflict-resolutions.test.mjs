import assert from 'node:assert/strict'
import test from 'node:test'
import {
  manualConflictResolutionPayload,
  resolveAllManualConflicts,
  resolveManualConflict,
  unresolvedManualConflictCount,
} from '../src/admin/manualConflictResolutions.ts'

const conflicts = [
  { sourceRowNumber: 2, field: 'gunTimeMs' },
  { sourceRowNumber: 2, field: 'chipTimeMs' },
  { sourceRowNumber: 3, field: 'status' },
]

test('manual conflicts remain unresolved until each field has an explicit decision', () => {
  let state = {}
  assert.equal(unresolvedManualConflictCount(conflicts, state), 3)
  state = resolveManualConflict(state, conflicts[0], 'KEEP_MANUAL')
  assert.equal(unresolvedManualConflictCount(conflicts, state), 2)
  state = resolveManualConflict(state, conflicts[1], 'ACCEPT_SOURCE')
  state = resolveManualConflict(state, conflicts[2], 'KEEP_MANUAL')
  assert.equal(unresolvedManualConflictCount(conflicts, state), 0)
  assert.deepEqual(manualConflictResolutionPayload(conflicts, state), [
    { sourceRowNumber: 2, field: 'gunTimeMs', decision: 'KEEP_MANUAL' },
    { sourceRowNumber: 2, field: 'chipTimeMs', decision: 'ACCEPT_SOURCE' },
    { sourceRowNumber: 3, field: 'status', decision: 'KEEP_MANUAL' },
  ])
})

test('bulk keep and bulk accept replace all earlier decisions deterministically', () => {
  const mixed = resolveManualConflict({}, conflicts[0], 'KEEP_MANUAL')
  const keep = resolveAllManualConflicts(conflicts, 'KEEP_MANUAL')
  const accept = resolveAllManualConflicts(conflicts, 'ACCEPT_SOURCE')

  assert.notDeepEqual(mixed, keep)
  assert.equal(unresolvedManualConflictCount(conflicts, keep), 0)
  assert.equal(unresolvedManualConflictCount(conflicts, accept), 0)
  assert.ok(manualConflictResolutionPayload(conflicts, keep)
    .every((resolution) => resolution.decision === 'KEEP_MANUAL'))
  assert.ok(manualConflictResolutionPayload(conflicts, accept)
    .every((resolution) => resolution.decision === 'ACCEPT_SOURCE'))

  const changedAfterBulk = resolveManualConflict(accept, conflicts[0], 'KEEP_MANUAL')
  assert.deepEqual(manualConflictResolutionPayload(conflicts, changedAfterBulk), [
    { sourceRowNumber: 2, field: 'gunTimeMs', decision: 'KEEP_MANUAL' },
    { sourceRowNumber: 2, field: 'chipTimeMs', decision: 'ACCEPT_SOURCE' },
    { sourceRowNumber: 3, field: 'status', decision: 'ACCEPT_SOURCE' },
  ])
})
