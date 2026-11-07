import assert from 'node:assert/strict'
import test from 'node:test'
import {
  MAX_RESULT_TIME_MS,
  RESULT_TIME_ERROR,
  formatResultTime,
  parseResultTime,
} from '../src/admin/resultTime.ts'

test('result time parser accepts only the documented whole-string formats', () => {
  assert.deepEqual(parseResultTime(''), { valid: true, milliseconds: null })
  assert.deepEqual(parseResultTime('  12:34  '), { valid: true, milliseconds: 754_000 })
  assert.deepEqual(parseResultTime('1:02:03'), { valid: true, milliseconds: 3_723_000 })
  assert.deepEqual(parseResultTime('01:24:35'), { valid: true, milliseconds: 5_075_000 })
  assert.deepEqual(parseResultTime('75:12.4'), { valid: true, milliseconds: 4_512_400 })
  assert.deepEqual(parseResultTime('24:35.1'), { valid: true, milliseconds: 1_475_100 })
  assert.deepEqual(parseResultTime('24:35.12'), { valid: true, milliseconds: 1_475_120 })
  assert.deepEqual(parseResultTime('24:35.123'), { valid: true, milliseconds: 1_475_123 })
  assert.deepEqual(parseResultTime('2:03:04.056'), { valid: true, milliseconds: 7_384_056 })

  for (const value of [
    'abc', 'тест', '24abc', '24:abc', 'abc24:35', '24:3ф', '24::35',
    '24:60', '1:60:00', '1:20:60', '-24:35', '1:-20:00', '24,35', '24.35',
    '24:35.', '24:35.1234', 'NaN', 'Infinity', '1:2', 'abc 1:02', '1:02 extra',
  ]) {
    assert.deepEqual(parseResultTime(value), {
      valid: false,
      milliseconds: null,
      error: RESULT_TIME_ERROR,
    })
  }
})

test('result time parser enforces the shared upper bound and formatter is canonical', () => {
  assert.deepEqual(parseResultTime('720:00:00'), { valid: true, milliseconds: MAX_RESULT_TIME_MS })
  assert.equal(parseResultTime('720:00:00.001').valid, false)
  assert.equal(formatResultTime(null), '')
  assert.equal(formatResultTime(754_000), '12:34')
  assert.equal(formatResultTime(3_723_004), '1:02:03.004')
  assert.equal(formatResultTime(MAX_RESULT_TIME_MS), '720:00:00')
})
