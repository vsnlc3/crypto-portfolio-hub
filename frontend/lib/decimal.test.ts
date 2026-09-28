import { describe, expect, it } from 'vitest'
import { decimalCompare, decimalNegate, decimalRatioPercent, decimalRatioToNumber, decimalShift, decimalSum, decimalToNumber } from './decimal'
import { formatAmount, formatJpy, formatMoney, formatPercent, formatSignedJpy } from './format'

describe('decimal string helpers', () => {
  it('adds and compares values without binary floating point loss', () => {
    expect(decimalSum(['0.1', '0.2', '-0.05'])).toBe('0.25')
    expect(() => decimalSum(['invalid'])).toThrow('Cannot sum an invalid decimal value')
    expect(decimalCompare('9007199254740993.01', '9007199254740993')).toBe(1)
    expect(decimalCompare('-0.0001', '0')).toBe(-1)
  })

  it('converts decimals to numbers only for visualization boundaries', () => {
    expect(decimalRatioToNumber('25', '100')).toBe(0.25)
    expect(decimalRatioPercent('1', '3')).toBe('33.3')
    expect(decimalRatioPercent('1', '0')).toBeNull()
    expect(decimalToNumber('1e3')).toBe(1000)
    expect(decimalShift('0.125', 2)).toBe('12.5')
    expect(decimalNegate('0.125')).toBe('-0.125')
  })

  it('formats plain decimal strings for JPY, currencies, quantities, and percentages', () => {
    expect(formatJpy('9007199254740993.49')).toBe('¥9,007,199,254,740,993')
    expect(formatJpy('9007199254740993.49', true)).toBe('¥9,007T')
    expect(formatJpy('999999.9', true)).toBe('¥1M')
    expect(formatSignedJpy('1500000', true)).toBe('+¥1.5M')
    expect(formatSignedJpy('-1500000', true)).toBe('-¥1.5M')
    expect(formatSignedJpy('-150.4')).toBe('-¥150')
    expect(formatMoney('0.00000001', 'USD')).toBe('$0.00000001')
    expect(formatAmount('0.00000049', 'BTC')).toBe('0 BTC')
    expect(formatPercent('2.75', 2)).toBe('+2.75%')
    expect(formatPercent('-0.01')).toBe('-0%')
  })
})
