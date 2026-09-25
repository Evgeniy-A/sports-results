import type { CanonicalImportField, ImportFileAnalysis } from './types'

export type ImportSourceKind = 'TEMPLATE' | 'EXTERNAL'
export type ExternalImportScope = 'SINGLE' | 'MULTI'

export interface ImportReadinessInput {
  analysis: ImportFileAnalysis
  sourceKind: ImportSourceKind
  externalScope: ExternalImportScope
  targetRaceId: number | null
  columnMappings: Record<string, CanonicalImportField>
  raceMappings: Record<string, number>
}

export interface ImportReadiness {
  problems: string[]
  scopeRaceIds: number[]
  mappedRaceCount: number
  importedFieldCount: number
  canValidate: boolean
}

export function calculateImportReadiness({
  analysis,
  sourceKind,
  externalScope,
  targetRaceId,
  columnMappings,
  raceMappings,
}: ImportReadinessInput): ImportReadiness {
  const importedFields = Object.values(columnMappings)
  const problems: string[] = []

  if (!analysis.sportsResultsTemplate) {
    const duplicate = importedFields.find((field, index) => importedFields.indexOf(field) !== index)
    if (duplicate) {
      const label = analysis.canonicalFields.find((field) => field.field === duplicate)?.displayName ?? duplicate
      problems.push(`Поле «${label}» выбрано для нескольких колонок.`)
    }
    for (const required of analysis.canonicalFields.filter((field) => field.required)) {
      if (!importedFields.includes(required.field)) {
        problems.push(`Не удалось определить обязательное поле: ${required.displayName}.`)
      }
    }
  }

  const isExternalMulti = sourceKind === 'EXTERNAL' && externalScope === 'MULTI'
  if (isExternalMulti) {
    if (!importedFields.includes('RACE')) {
      problems.push('Сопоставьте колонку, которая определяет Старт.')
    } else if (analysis.raceValues.length === 0) {
      problems.push('Нажмите «Найти старты в файле», чтобы сопоставить их со стартами мероприятия.')
    } else {
      const unresolved = analysis.raceValues.filter((value) => !raceMappings[value.sourceValue])
      if (unresolved.length === 1) {
        problems.push(`Остался несопоставленный старт: ${unresolved[0].sourceValue}.`)
      } else if (unresolved.length > 1) {
        problems.push(`Сопоставьте все ${analysis.raceValues.length} старта из файла, чтобы продолжить.`)
      }
    }
  }

  const scopeRaceIds = analysis.sportsResultsTemplate
    ? analysis.resolvedRaceIds
    : sourceKind === 'EXTERNAL' && externalScope === 'SINGLE'
      ? targetRaceId === null ? [] : [targetRaceId]
      : [...new Set(
          analysis.raceValues
            .map((value) => raceMappings[value.sourceValue])
            .filter((raceId): raceId is number => Number.isInteger(raceId) && raceId > 0),
        )].sort((left, right) => left - right)

  if (scopeRaceIds.length === 0 && problems.length === 0) {
    problems.push('Не определён ни один Старт для проверки данных.')
  }

  const mappedRaceCount = analysis.raceValues.filter(
    (value) => Number.isInteger(raceMappings[value.sourceValue]) && raceMappings[value.sourceValue] > 0,
  ).length

  return {
    problems,
    scopeRaceIds,
    mappedRaceCount,
    importedFieldCount: new Set(importedFields).size,
    canValidate: problems.length === 0 && scopeRaceIds.length > 0,
  }
}
