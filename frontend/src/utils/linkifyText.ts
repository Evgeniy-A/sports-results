export type LinkifiedTextPart = {
  text: string
  href?: string
}

const HTTP_URL_PATTERN = /https?:\/\/[^\s<]+/g
const TRAILING_PUNCTUATION = /[.,!?;:'"»”\]}]$/

function withoutTrailingPunctuation(candidate: string): string {
  let value = candidate
  while (TRAILING_PUNCTUATION.test(value)) value = value.slice(0, -1)
  while (value.endsWith(')')) {
    const openingCount = [...value].filter((character) => character === '(').length
    const closingCount = [...value].filter((character) => character === ')').length
    if (closingCount <= openingCount) break
    value = value.slice(0, -1)
  }
  return value
}

export function linkifyHttpText(value: string): LinkifiedTextPart[] {
  const parts: LinkifiedTextPart[] = []
  let cursor = 0

  for (const match of value.matchAll(HTTP_URL_PATTERN)) {
    const index = match.index
    const rawUrl = match[0]
    const href = withoutTrailingPunctuation(rawUrl)
    if (index > cursor) parts.push({ text: value.slice(cursor, index) })
    const hasHost = href.startsWith('http://')
      ? href.length > 'http://'.length
      : href.length > 'https://'.length
    if (hasHost) parts.push({ text: href, href })
    else parts.push({ text: rawUrl })
    if (href.length < rawUrl.length) parts.push({ text: rawUrl.slice(href.length) })
    cursor = index + rawUrl.length
  }

  if (cursor < value.length) parts.push({ text: value.slice(cursor) })
  return parts
}
