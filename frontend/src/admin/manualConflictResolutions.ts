import type {
  ImportManualConflict,
  ManualConflictDecision,
  ManualConflictResolution,
} from './types'

export type ManualConflictResolutionState = Record<string, ManualConflictDecision>

export function manualConflictKey(conflict: Pick<ImportManualConflict, 'sourceRowNumber' | 'field'>): string {
  return `${conflict.sourceRowNumber}:${conflict.field}`
}

export function resolveManualConflict(
  current: ManualConflictResolutionState,
  conflict: ImportManualConflict,
  decision: ManualConflictDecision,
): ManualConflictResolutionState {
  return { ...current, [manualConflictKey(conflict)]: decision }
}

export function resolveAllManualConflicts(
  conflicts: ImportManualConflict[],
  decision: ManualConflictDecision,
): ManualConflictResolutionState {
  return Object.fromEntries(conflicts.map((conflict) => [manualConflictKey(conflict), decision]))
}

export function unresolvedManualConflictCount(
  conflicts: ImportManualConflict[],
  resolutions: ManualConflictResolutionState,
): number {
  return conflicts.filter((conflict) => resolutions[manualConflictKey(conflict)] === undefined).length
}

export function manualConflictResolutionPayload(
  conflicts: ImportManualConflict[],
  resolutions: ManualConflictResolutionState,
): ManualConflictResolution[] {
  return conflicts.flatMap((conflict) => {
    const decision = resolutions[manualConflictKey(conflict)]
    return decision === undefined ? [] : [{
      sourceRowNumber: conflict.sourceRowNumber,
      field: conflict.field,
      decision,
    }]
  })
}
