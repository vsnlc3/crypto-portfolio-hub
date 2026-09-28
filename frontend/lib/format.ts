export function formatAmount(value: number, unit?: string) {
  const abs = Math.abs(value)
  const maximumFractionDigits = abs >= 1000 ? 2 : abs >= 1 ? 4 : 6
  const formatted = new Intl.NumberFormat('en-US', { maximumFractionDigits }).format(value)
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

export function formatJpy(value: number | null, compact = false) {
  if (value === null || !Number.isFinite(value)) return 'Unavailable'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'JPY',
    maximumFractionDigits: 0,
    ...(compact ? { notation: 'compact', maximumSignificantDigits: 4 } : {}),
  }).format(value)
}

export function formatSignedJpy(value: number | null, compact = false) {
  if (value === null || !Number.isFinite(value)) return 'Unavailable'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'JPY',
    maximumFractionDigits: 0,
    signDisplay: 'always',
    ...(compact ? { notation: 'compact', maximumSignificantDigits: 4 } : {}),
  }).format(value)
}

export function formatPercent(value: number | null) {
  if (value === null || !Number.isFinite(value)) return 'Unavailable'
  return new Intl.NumberFormat('en-US', {
    style: 'percent',
    maximumFractionDigits: 1,
    signDisplay: 'always',
  }).format(value / 100)
}

export function formatMoney(value: number | null, currency: string | null) {
  if (value === null || currency === null) return 'Unavailable'
  if (!/^[A-Z]{3}$/.test(currency)) return `${formatAmount(value)} ${currency}`
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: Math.abs(value) >= 1_000 ? 2 : Math.abs(value) >= 1 ? 4 : 8,
  }).format(value)
}
