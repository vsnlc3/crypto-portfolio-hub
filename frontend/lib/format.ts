import {
  decimalCompare,
  decimalNegate,
  decimalShift,
  formatCurrency,
  formatDecimal,
  type DecimalValue,
} from './decimal'

export function formatAmount(value: DecimalValue, unit?: string) {
  const maximumFractionDigits = absGreaterThanOrEqual(value, '1000') ? 2 : absGreaterThanOrEqual(value, '1') ? 4 : 6
  const formatted = formatDecimal(value, maximumFractionDigits)
  return unit ? `${formatted} ${unit}` : formatted
}

export function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatRelative(iso: string | null) {
  if (!iso) return 'Not synced yet'
  const timestamp = Date.parse(iso)
  if (!Number.isFinite(timestamp)) return 'Time unavailable'
  const elapsedSeconds = Math.round((timestamp - Date.now()) / 1000)
  const formatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
  if (Math.abs(elapsedSeconds) < 60) return formatter.format(elapsedSeconds, 'second')
  if (Math.abs(elapsedSeconds) < 3_600) return formatter.format(Math.round(elapsedSeconds / 60), 'minute')
  if (Math.abs(elapsedSeconds) < 86_400) return formatter.format(Math.round(elapsedSeconds / 3_600), 'hour')
  return formatter.format(Math.round(elapsedSeconds / 86_400), 'day')
}

export function formatJpy(value: DecimalValue | null, compact = false) {
  if (value === null) return 'Unavailable'
  if (!compact) return formatCurrency(value, 'JPY', 0)
  return formatCompactJpy(value, false)
}

export function formatSignedJpy(value: DecimalValue | null, compact = false) {
  if (value === null) return 'Unavailable'
  if (!compact) return formatCurrency(value, 'JPY', 0, true)
  return formatCompactJpy(value, true)
}

export function formatPercent(value: DecimalValue | null, maximumFractionDigits = 1) {
  if (value === null) return 'Unavailable'
  const formatted = formatDecimal(value, maximumFractionDigits)
  const signed = decimalCompare(value, '0') < 0 ? formatted : `+${formatted.replace(/^-/, '')}`
  return `${signed}%`
}

export function formatMoney(value: DecimalValue | null, currency: string | null, signed = false) {
  if (value === null || currency === null) return 'Unavailable'
  if (!/^[A-Z]{3}$/.test(currency)) {
    const sign = signed && decimalCompare(value, '0') > 0 ? '+' : ''
    return `${sign}${formatAmount(value)} ${currency}`
  }
  const maximumFractionDigits = currency === 'JPY'
    ? 0
    : absGreaterThanOrEqual(value, '1000') ? 2 : absGreaterThanOrEqual(value, '1') ? 4 : 8
  return formatCurrency(value, currency, maximumFractionDigits, signed)
}

function absGreaterThanOrEqual(value: DecimalValue, threshold: string) {
  const comparison = decimalCompare(value, '0')
  if (Number.isNaN(comparison)) return false
  return comparison >= 0
    ? decimalCompare(value, threshold) >= 0
    : decimalCompare(value, `-${threshold}`) <= 0
}

function formatCompactJpy(value: DecimalValue, signed: boolean) {
  const negative = decimalCompare(value, '0') < 0
  const absolute = negative ? decimalNegate(value) : value
  if (absolute === null) return 'Unavailable'
  const units = [
    { threshold: '1000000000000', places: 12, suffix: 'T' },
    { threshold: '1000000000', places: 9, suffix: 'B' },
    { threshold: '1000000', places: 6, suffix: 'M' },
    { threshold: '1000', places: 3, suffix: 'K' },
  ]
  for (let index = 0; index < units.length; index += 1) {
    const unit = units[index]
    if (decimalCompare(absolute, unit.threshold) < 0) continue
    const shifted = decimalShift(absolute, -unit.places)
    if (shifted === null) return 'Unavailable'
    let significantIntegerDigits = shifted.split('.')[0].length
    let maximumFractionDigits = Math.max(0, 4 - significantIntegerDigits)
    const rounded = formatDecimal(shifted, maximumFractionDigits).replaceAll(',', '')
    if (decimalCompare(rounded, '1000') >= 0 && index > 0) {
      const largerUnit = units[index - 1]
      const largerShifted = decimalShift(absolute, -largerUnit.places)
      if (largerShifted === null) return 'Unavailable'
      significantIntegerDigits = largerShifted.split('.')[0].length
      maximumFractionDigits = Math.max(0, 4 - significantIntegerDigits)
      const displayValue = negative ? decimalNegate(largerShifted) ?? largerShifted : largerShifted
      return `${formatCurrency(displayValue, 'JPY', maximumFractionDigits, signed)}${largerUnit.suffix}`
    }
    const displayValue = negative ? decimalNegate(shifted) ?? shifted : shifted
    return `${formatCurrency(displayValue, 'JPY', maximumFractionDigits, signed)}${unit.suffix}`
  }
  return formatCurrency(value, 'JPY', 0, signed)
}
