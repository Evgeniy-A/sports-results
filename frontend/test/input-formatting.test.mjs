import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  formatDateInput,
  formatDurationInput,
  formatInputEdit,
  separatorNavigationCaret,
} from '../src/utils/inputFormatting.ts'
import { parseRussianBirthDate } from '../src/utils/resultInquiry.ts'
import { parseClaimedTime } from '../src/utils/resultIssue.ts'
import { formatDuration } from '../src/utils/format.ts'

const autoformatSource = await readFile(
  new URL('../src/components/DigitAutoformatInput.tsx', import.meta.url),
  'utf8',
)
const panelSource = await readFile(
  new URL('../src/components/ResultInquiryPanel.tsx', import.meta.url),
  'utf8',
)
const dialogSource = await readFile(
  new URL('../src/components/ResultIssueDialog.tsx', import.meta.url),
  'utf8',
)
const resultEditorSource = await readFile(
  new URL('../src/admin/components/AdminResultEditor.tsx', import.meta.url),
  'utf8',
)

test('DOB digits are progressively formatted without becoming a default value', () => {
  assert.equal(formatDateInput('1'), '1')
  assert.equal(formatDateInput('16'), '16')
  assert.equal(formatDateInput('160'), '16.0')
  assert.equal(formatDateInput('1608'), '16.08')
  assert.equal(formatDateInput('160819'), '16.08.19')
  assert.equal(formatDateInput('16081990'), '16.08.1990')
  assert.match(panelSource, /useState\(''\)/)
  assert.match(dialogSource, /const \[birthDate, setBirthDate\] = useState\(''\)/)
})

test('DOB paste, letters, backspace, selection and clearing remain editable', () => {
  assert.equal(formatDateInput('16.08.1990'), '16.08.1990')
  assert.equal(formatDateInput('a1b6-0x8/1q9z9 0'), '16.08.1990')
  assert.deepEqual(formatInputEdit('16.08.199', 9, formatDateInput), {
    value: '16.08.199', caret: 9,
  })
  assert.deepEqual(formatInputEdit('', 0, formatDateInput), { value: '', caret: 0 })
  assert.equal(separatorNavigationCaret('16.08.1990', 3, 3, 'Backspace'), 2)
  assert.equal(separatorNavigationCaret('16.08.1990', 2, 2, 'Delete'), 3)
  assert.equal(separatorNavigationCaret('16.08.1990', 0, 10, 'Backspace'), null)
})

test('DOB mask keeps calendar validation and ISO backend conversion', () => {
  assert.equal(parseRussianBirthDate(formatDateInput('31022000')), null)
  assert.equal(parseRussianBirthDate(formatDateInput('16081990')), '1990-08-16')
})

test('sporting duration digits and formatted paste use HH:MM:SS[.mmm]', () => {
  assert.equal(formatDurationInput('012345'), '01:23:45')
  assert.equal(formatDurationInput('0123456'), '01:23:45.6')
  assert.equal(formatDurationInput('01234567'), '01:23:45.67')
  assert.equal(formatDurationInput('012345678'), '01:23:45.678')
  assert.equal(formatDurationInput('01:23:45.678'), '01:23:45.678')
  assert.equal(formatDurationInput('251245'), '25:12:45')
  assert.equal(formatDurationInput('123:45:56.789'), '123:45:56.789')
})

test('sporting duration autoformat restarts after complete clearing', () => {
  assert.deepEqual(formatInputEdit('', 0, formatDurationInput), { value: '', caret: 0 })
  assert.deepEqual(formatInputEdit('012345678', 9, formatDurationInput), {
    value: '01:23:45.678', caret: 12,
  })
  assert.equal(formatDurationInput(''), '')
  assert.equal(formatDurationInput('012345678'), '01:23:45.678')
  assert.equal(formatDurationInput('01:23:45.678'), '01:23:45.678')
})

test('sporting duration display always uses millisecond precision', () => {
  assert.equal(formatDuration(1_000), '00:00:01.000')
  assert.equal(formatDuration(1_001), '00:00:01.001')
  assert.equal(formatDuration(2_029_340), '00:33:49.340')
  assert.equal(formatDuration(90_765_120), '25:12:45.120')
})

test('duration validation rejects invalid minutes and seconds and preserves milliseconds', () => {
  assert.equal(parseClaimedTime(formatDurationInput('016045')), null)
  assert.equal(parseClaimedTime(formatDurationInput('012360')), null)
  assert.equal(parseClaimedTime(formatDurationInput('251245')), 90_765_000)
  assert.equal(parseClaimedTime(formatDurationInput('012345678')), 5_025_678)
})

test('both DOB surfaces and claimed duration share the numeric-friendly input', () => {
  assert.match(panelSource, /DigitAutoformatInput[\s\S]*formatter=\{formatDateInput\}/)
  assert.match(dialogSource, /DigitAutoformatInput[\s\S]*formatter=\{formatDateInput\}/)
  assert.match(dialogSource, /DigitAutoformatInput[\s\S]*formatter=\{formatDurationInput\}/)
  assert.match(autoformatSource, /inputMode="numeric"/)
  assert.match(autoformatSource, /selectionStart/)
  assert.match(autoformatSource, /setSelectionRange/)
  assert.match(dialogSource, /placeholder="ЧЧ:ММ:СС\.ммм"/)
  assert.match(resultEditorSource, /DigitAutoformatInput[\s\S]*formatter=\{formatDurationInput\}/)
  assert.doesNotMatch(resultEditorSource, /aria-label="Официальное время"[\s\S]*onChange=/)
})
