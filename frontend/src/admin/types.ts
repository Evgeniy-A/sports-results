import type {
  Category,
  EventSummary,
  EventDocument,
  EventInfoBlock,
  EventScheduleItem,
  PageResponse,
  Race,
  RankingAchievement,
  ResultListItem,
} from '../api/types'

export type { EventDocument, EventInfoBlock, EventScheduleItem, EventSummary, PageResponse, Race, ResultListItem }

export interface AdminCredentials {
  username: string
  password: string
}

export interface EventSeries {
  id: number
  name: string
  slug: string
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface AdminCategory {
  id: number
  raceId: number
  sourceName: string
  displayName: string
  minAge: number | null
  maxAge: number | null
  gender: 'MALE' | 'FEMALE' | null
  displayOrder: number
  enabled: boolean
}

export interface StartCluster {
  id: number
  raceId: number
  code: string | null
  sourceName: string | null
  displayName: string
  displayOrder: number
  startsAt: string | null
}

export interface AwardPolicy {
  id: number
  raceId: number
  rankingBasis: 'GUN_TIME' | 'CHIP_TIME' | 'NONE'
  primaryStandingMode: 'ALL' | 'BY_GENDER' | 'NONE'
  absolutePrizePlaces: number
  categoryEnabled: boolean
  ageCalculationMode: 'EVENT_DATE' | 'END_OF_EVENT_YEAR'
  categoryPrizePlaces: number
  excludeAbsoluteWinnersFromCategory: boolean
}

export type AwardPolicyUpdate = Pick<
  AwardPolicy,
  | 'rankingBasis'
  | 'primaryStandingMode'
  | 'absolutePrizePlaces'
  | 'categoryEnabled'
  | 'ageCalculationMode'
  | 'categoryPrizePlaces'
  | 'excludeAbsoluteWinnersFromCategory'
>

export interface ResultInquirySettings {
  enabled: boolean
  windowDays: number | null
  email: string | null
}

export interface ParticipantInfo {
  id: number
  shortDescription: string | null
  venueName: string | null
  venueAddress: string | null
  locationDescription: string | null
  latitude: number | null
  longitude: number | null
  additionalInfo: string | null
}

export interface AdminResultDetails extends Omit<ResultListItem, 'category' | 'entryKind'> {
  eventId: number
  birthDate: string | null
  sourceCategory: string | null
  entryKind: 'PERSON' | 'TEAM' | 'OTHER' | 'UNKNOWN'
  clusterId: number | null
  clusterName: string | null
  effectiveCategory: Category | null
  splits: Array<{
    checkpointId: number
    checkpointName: string
    elapsedTimeMs: number | null
    recordedAt: string | null
  }>
}

export type ImportMode = 'ADD_NEW' | 'UPDATE_EXISTING' | 'EMERGENCY_REPLACE'

export interface ImportPreview {
  operationId: string
  operationStatus: string
  mode: ImportMode
  raceIds: number[]
  fileSha256: string
  baseRevision: number
  planDigest: string
  createdAt: string
  expiresAt: string
  totals: {
    totalRows: number
    inScopeRows: number
    newCount: number
    unchangedCount: number
    changedCount: number
    ambiguousCount: number
    conflictCount: number
    invalidCount: number
    duplicateCount: number
    outOfScopeCount: number
  }
  modeSummary: {
    wouldInsert: number
    wouldUpdate: number
    existingSkipped: number
    newSkipped: number
    unchanged: number
    blocked: number
  }
  blockingErrorsPresent: boolean
  rowLimit: number
  rowsTruncated: boolean
  rows: ImportPreviewRow[]
  diagnostics: Array<{
    sourceRowNumber: number | null
    field: string | null
    code: string
    message: string
  }>
  diagnosticsTruncated: boolean
  emergencySummary: null | {
    dangerousOperation: boolean
    event: { name: string; location: string | null; startsAt: string | null }
    file: { filename: string; sha256: string }
    races: Array<{
      race: ImportRaceRef
      resultsPublicationStatus: 'DRAFT' | 'PUBLISHED'
      currentCount: number
      sourceCount: number
      wouldRetireCount: number
      wouldInsertCount: number
      activeIssuesWouldArchiveCount: number
    }>
    totals: {
      currentCount: number
      sourceCount: number
      retireCount: number
      insertCount: number
      activeIssuesArchiveCount: number
      outOfScopeCount: number
      blockingCount: number
    }
  }
}

export interface ImportRaceRef {
  raceId: number
  raceName: string
}

export interface ImportPreviewRow {
  sourceRowNumber: number
  bib: string | null
  participantName: string | null
  targetRace: ImportRaceRef | null
  matchedRegistrationId: number | null
  matchedResultId: number | null
  decision: string
  futureAction: string
  reasonCode: string
  reason: string
  diffs: Array<{ field: string; oldValue: string | null; newValue: string | null }>
}

export interface ImportApplyResult {
  operationId: string
  status: string
  mode: ImportMode
  eventId: number
  importBatchId: number
  insertedCount: number
  updatedCount: number
  resultCreatedCount: number
  newSkippedCount: number
  existingSkippedCount: number
  unchangedCount: number
  outOfScopeCount: number
  retiredCount: number
  archivedIssueCount: number
  newRevision: number
  appliedAt: string
}

export interface RecalculationPreview {
  operationId: string
  status: string
  event: { id: number; name: string }
  races: Array<{ id: number; name: string }>
  createdAt: string
  expiresAt: string
  currentRegistrationCount: number
  minorCount: number
  adultCount: number
  noBirthDateCount: number
  changedCategoryCount: number
  unchangedCategoryCount: number
  blockingCount: number
  rankingAffected: boolean
  rowsTruncated: boolean
  rows: Array<{
    registrationId: number
    bib: string | null
    oldEffectiveCategory: string | null
    newEffectiveCategory: string | null
    reason: string
    blockingCode: string | null
  }>
}

export type IssueStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED'
export type IssueType = 'MISSING_RESULT' | 'RESULT_CORRECTION'
export type QueueScope = 'CURRENT' | 'ARCHIVED' | 'ALL'

export interface EventIssueItem {
  issueId: number
  status: IssueStatus
  issueType: IssueType
  correctionReason: string | null
  createdAt: string
  queueArchivedAt: string | null
  registration: { registrationId: number; bib: string | null; displayName: string }
  race: { raceId: number; raceName: string }
  resultId: number | null
  attachmentCount: number
}

export interface IssueAttachment {
  attachmentId: number
  originalFileName: string
  contentType: string | null
  detectedContentType: string | null
  sizeBytes: number
  uploadStatus: string
  scanStatus: string
  createdAt: string
  uploadedAt: string | null
  scannedAt: string | null
  deletedAt: string | null
}

export interface IssueSnapshot {
  origin: string
  eventName: string
  eventLocation: string | null
  eventStartsAt: string | null
  sportFormatName: string | null
  raceName: string
  raceCode: string | null
  bib: string | null
  displayName: string
  effectiveCategoryName: string | null
  sourceCategory: string | null
  observedGunTimeMs: number | null
  observedChipTimeMs: number | null
  observedResultStatus: string | null
  rankingAchievements: RankingAchievement[] | null
}

export interface EventIssueDetail {
  issueId: number
  eventId: number
  issueType: IssueType
  correctionReason: string | null
  status: IssueStatus
  contactEmail: string
  message: string
  createdAt: string
  updatedAt: string
  resolvedAt: string | null
  claimedGunTimeMs: number | null
  claimedChipTimeMs: number | null
  estimatedStartAt: string | null
  estimatedFinishAt: string | null
  archive: {
    issueId: number
    status: IssueStatus
    queueArchivedAt: string | null
    queueArchivedBy: string | null
    queueArchiveReason: string | null
    queueArchivedImportOperationId: string | null
  }
  snapshot: IssueSnapshot
  registration: null | {
    registrationId: number
    bib: string | null
    displayName: string
    sourceCategory: string | null
    entryKind: string
    effectiveCategory: Category | null
    raceId: number
    raceName: string
  }
  result: null | {
    resultId: number
    status: string
    gunTimeMs: number | null
    chipTimeMs: number | null
    rankingAchievements: RankingAchievement[]
  }
  attachments: IssueAttachment[]
}

export interface JournalItem {
  issueId: number
  issueType: IssueType
  correctionReason: string | null
  status: IssueStatus
  createdAt: string
  queueArchivedAt: string | null
  event: { eventId: number; eventName: string; location: string | null; startsAt: string | null }
  sportFormat: { sportFormatId: number | null; name: string | null; code: string | null }
  race: { raceId: number | null; name: string; code: string | null; distanceMeters: number | null }
  participant: { registrationId: number | null; bib: string | null; displayName: string }
  observedResult: { status: string | null; gunTimeMs: number | null; chipTimeMs: number | null }
  categoryName: string | null
  attachmentCount: number
}

export interface JournalDetail {
  issueId: number
  eventId: number
  issueType: IssueType
  correctionReason: string | null
  status: IssueStatus
  contactEmail: string
  message: string
  createdAt: string
  updatedAt: string
  resolvedAt: string | null
  claimedGunTimeMs: number | null
  claimedChipTimeMs: number | null
  queueArchivedAt: string | null
  queueArchivedBy: string | null
  queueArchiveReason: string | null
  historicalSnapshot: IssueSnapshot
  currentContext: {
    registrationExists: boolean
    registrationRetired: boolean
    registrationId: number | null
    race: null | { raceId: number; name: string; code: string | null; distanceMeters: number | null }
    category: null | { categoryId: number; name: string }
    result: null | { resultId: number; status: string; gunTimeMs: number | null; chipTimeMs: number | null }
  }
  attachmentCount: number
  attachments: IssueAttachment[]
  history: Array<{
    historyId: number
    action: string
    fromStatus: IssueStatus | null
    toStatus: IssueStatus | null
    actor: string | null
    reason: string | null
    createdAt: string
  }>
}

export interface JournalFilters {
  issueId?: number
  eventId?: number
  location?: string
  eventDateFrom?: string
  eventDateTo?: string
  createdFrom?: string
  createdTo?: string
  raceId?: number
  bib?: string
  participant?: string
  issueType?: IssueType
  correctionReason?: string
  status?: IssueStatus[]
  queueScope?: QueueScope
  page?: number
  size?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

export interface ShareBatch {
  batchId: string
  createdBy: string
  createdAt: string
  expiresAt: string | null
  revokedAt: string | null
  revokedBy: string | null
  matchedIssueCount: number
  grantCount: number
  accessedGrantCount: number
}
