export type DecimalString = string
export type DecimalValue = DecimalString | number

type DecimalParts = {
  negative: boolean
  integer: string
  fraction: string
}

function decimalParts(value: DecimalValue): DecimalParts | null {
  const input = String(value).trim()
  const match = /^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(input)
  if (!match) return null

  const negative = match[1] === '-'
  const digits = `${match[2]}${match[3] ?? ''}`
  const decimalPosition = match[2].length + Number(match[4] ?? 0)
  let integer: string
  let fraction: string
  if (decimalPosition <= 0) {
    integer = '0'
    fraction = `${'0'.repeat(Math.min(-decimalPosition, 10_000))}${digits}`
  } else if (decimalPosition >= digits.length) {
    integer = `${digits}${'0'.repeat(Math.min(decimalPosition - digits.length, 10_000))}`
    fraction = ''
  } else {
    integer = digits.slice(0, decimalPosition)
    fraction = digits.slice(decimalPosition)
  }
  integer = integer.replace(/^0+(?=\d)/, '') || '0'
  return { negative, integer, fraction }
}

function coefficient(parts: DecimalParts): bigint {
  const magnitude = BigInt(`${parts.integer}${parts.fraction}` || '0')
  return parts.negative ? -magnitude : magnitude
}

function renderCoefficient(value: bigint, scale: number): string {
  const negative = value < 0n
  const digits = (negative ? -value : value).toString().padStart(scale + 1, '0')
  const integer = scale === 0 ? digits : digits.slice(0, -scale)
  const fraction = scale === 0 ? '' : digits.slice(-scale).replace(/0+$/, '')
  const rendered = fraction ? `${integer}.${fraction}` : integer
  return negative && value !== 0n ? `-${rendered}` : rendered
}

function canonical(value: DecimalValue): string | null {
  const parts = decimalParts(value)
  if (!parts) return null
  return renderCoefficient(coefficient(parts), parts.fraction.length)
}

export function decimalCompare(left: DecimalValue, right: DecimalValue): number {
  const leftParts = decimalParts(left)
  const rightParts = decimalParts(right)
  if (!leftParts || !rightParts) return Number.NaN
  const scale = Math.max(leftParts.fraction.length, rightParts.fraction.length)
  const leftCoefficient = coefficient(leftParts) * 10n ** BigInt(scale - leftParts.fraction.length)
  const rightCoefficient = coefficient(rightParts) * 10n ** BigInt(scale - rightParts.fraction.length)
  return leftCoefficient < rightCoefficient ? -1 : leftCoefficient > rightCoefficient ? 1 : 0
}

export function decimalSum(values: readonly DecimalValue[]): DecimalString {
  const decimals = values.map((value) => {
    const parsed = decimalParts(value)
    if (!parsed) throw new TypeError('Cannot sum an invalid decimal value')
    return parsed
  })
  const scale = decimals.reduce((max, value) => Math.max(max, value.fraction.length), 0)
  const sum = decimals.reduce(
    (total, value) => total + coefficient(value) * 10n ** BigInt(scale - value.fraction.length),
    0n,
  )
  return renderCoefficient(sum, scale)
}

export function decimalToNumber(value: DecimalValue | null): number | null {
  if (value === null) return null
  const rendered = canonical(value)
  if (rendered === null) return null
  const result = Number(rendered)
  return Number.isFinite(result) ? result : null
}

export function decimalNegate(value: DecimalValue): DecimalString | null {
  const rendered = canonical(value)
  if (rendered === null) return null
  return rendered.startsWith('-') ? rendered.slice(1) : rendered === '0' ? '0' : `-${rendered}`
}

export function decimalRatioToNumber(numerator: DecimalValue, denominator: DecimalValue): number | null {
  const top = decimalToNumber(numerator)
  const bottom = decimalToNumber(denominator)
  if (top === null || bottom === null || bottom === 0) return null
  const result = top / bottom
  return Number.isFinite(result) ? result : null
}

export function decimalRatioPercent(
  numerator: DecimalValue,
  denominator: DecimalValue,
  fractionDigits = 1,
): DecimalString | null {
  const top = decimalParts(numerator)
  const bottom = decimalParts(denominator)
  if (!top || !bottom || !Number.isInteger(fractionDigits) || fractionDigits < 0) return null
  let topCoefficient = coefficient(top) * 100n
  let bottomCoefficient = coefficient(bottom)
  if (bottomCoefficient === 0n) return null
  const exponent = bottom.fraction.length - top.fraction.length + fractionDigits
  if (exponent >= 0) topCoefficient *= 10n ** BigInt(exponent)
  else bottomCoefficient *= 10n ** BigInt(-exponent)
  if (bottomCoefficient < 0n) {
    topCoefficient = -topCoefficient
    bottomCoefficient = -bottomCoefficient
  }
  const negative = topCoefficient < 0n
  const magnitude = negative ? -topCoefficient : topCoefficient
  let rounded = magnitude / bottomCoefficient
  if (magnitude % bottomCoefficient * 2n >= bottomCoefficient) rounded += 1n
  return renderCoefficient(negative ? -rounded : rounded, fractionDigits)
}

export function decimalShift(value: DecimalValue, places: number): DecimalString | null {
  const parts = decimalParts(value)
  if (!parts || !Number.isInteger(places)) return null
  const coefficientDigits = `${parts.integer}${parts.fraction}`
  const decimalPosition = parts.integer.length + places
  let integer: string
  let fraction: string
  if (decimalPosition <= 0) {
    integer = '0'
    fraction = `${'0'.repeat(Math.min(-decimalPosition, 10_000))}${coefficientDigits}`
  } else if (decimalPosition >= coefficientDigits.length) {
    integer = `${coefficientDigits}${'0'.repeat(Math.min(decimalPosition - coefficientDigits.length, 10_000))}`
    fraction = ''
  } else {
    integer = coefficientDigits.slice(0, decimalPosition)
    fraction = coefficientDigits.slice(decimalPosition)
  }
  return canonical(`${parts.negative ? '-' : ''}${integer || '0'}${fraction ? `.${fraction}` : ''}`)
}

export function formatDecimal(value: DecimalValue, maximumFractionDigits: number, minimumFractionDigits = 0): string {
  const parts = decimalParts(value)
  if (!parts || !Number.isInteger(maximumFractionDigits) || maximumFractionDigits < 0
    || !Number.isInteger(minimumFractionDigits) || minimumFractionDigits < 0
    || minimumFractionDigits > maximumFractionDigits) return 'Unavailable'
  const precision = BigInt(maximumFractionDigits)
  const scale = 10n ** precision
  const fraction = `${parts.fraction}${'0'.repeat(Math.max(0, maximumFractionDigits + 1 - parts.fraction.length))}`
  const truncatedFraction = fraction.slice(0, maximumFractionDigits)
  let rounded = BigInt(parts.integer) * scale + BigInt(truncatedFraction || '0')
  if (fraction[maximumFractionDigits] >= '5') rounded += 1n

  const integer = rounded / scale
  const remainder = maximumFractionDigits === 0 ? '' : (rounded % scale).toString().padStart(maximumFractionDigits, '0')
  const formattedInteger = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(integer)
  const formattedFraction = remainder.replace(/0+$/, '').padEnd(minimumFractionDigits, '0')
  const formatted = formattedFraction ? `${formattedInteger}.${formattedFraction}` : formattedInteger
  return parts.negative ? `-${formatted}` : formatted
}

export function formatCurrency(value: DecimalValue, currency: string, maximumFractionDigits: number, signed = false): string {
  const parts = decimalParts(value)
  if (!parts) return 'Unavailable'
  const currencyDefaults = new Intl.NumberFormat('en-US', { style: 'currency', currency }).resolvedOptions()
  const formatter = new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 0, signDisplay: signed ? 'always' : 'auto' })
  const pattern = formatter.formatToParts(parts.negative ? -1 : 1)
  const numericTypes = new Set(['integer', 'group', 'decimal', 'fraction'])
  const firstNumeric = pattern.findIndex((part) => part.type === 'integer')
  let lastNumeric = firstNumeric
  for (let index = firstNumeric; index < pattern.length; index += 1) {
    if (numericTypes.has(pattern[index].type)) lastNumeric = index
  }
  const magnitude = formatDecimal(
    `${parts.integer}${parts.fraction ? `.${parts.fraction}` : ''}`,
    maximumFractionDigits,
    Math.min(currencyDefaults.minimumFractionDigits ?? 0, maximumFractionDigits),
  )
  return `${pattern.slice(0, firstNumeric).map((part) => part.value).join('')}${magnitude}${pattern.slice(lastNumeric + 1).map((part) => part.value).join('')}`
}
