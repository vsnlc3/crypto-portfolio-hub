import { afterEach, describe, expect, it, vi } from 'vitest'
import { getActivities } from './activities-api'
import { getAssets } from './assets-api'
import { getCurrentUser, logout } from './auth-api'
import { ConnectionsApiError, createConnection, getConnections } from './connections-api'
import { demoModeStorageKey, disableDemoMode, enableDemoMode } from './demo-mode'
import { getPortfolioHistory, getPortfolioSummary } from './portfolio-api'
import { getPositions } from './positions-api'

afterEach(() => {
  disableDemoMode()
  vi.unstubAllGlobals()
})

describe('demo API fixture adapter', () => {
  it('serves all read-only API DTOs from fixtures without calling the Backend', async () => {
    enableDemoMode()
    const backendFetch = vi.fn()
    vi.stubGlobal('fetch', backendFetch)

    const user = await getCurrentUser()
    const connections = await getConnections()
    const summary = await getPortfolioSummary()
    const history = await getPortfolioHistory('30D')
    const assets = await getAssets()
    const positions = await getPositions()
    const activities = await getActivities()

    expect(user).toMatchObject({ id: 'demo-read-only-user', isDemo: true })
    expect(connections.map((connection) => connection.provider)).toEqual(['BITBANK', 'SOLANA', 'HYPERLIQUID'])
    expect(connections.some((connection) => connection.portfolioValue.status === 'STALE')).toBe(true)
    expect(summary.summary.status).toBe('STALE')
    expect(history.points).toHaveLength(4)
    expect(history.points.some((point) => point.status === 'STALE')).toBe(true)
    expect(assets.assets.map((asset) => asset.symbol)).toEqual(['BTC', 'ETH', 'SOL', 'USDC'])
    expect(positions.positions[0].priceCurrency).toBe('USD')
    expect(positions.positions[0].marginCurrency).toBe('USDC')
    expect(activities.activities[0].legs.map((leg) => leg.direction)).toEqual(['OUT', 'IN', 'FEE'])
    expect(activities.activities[1].perpetualFill?.direction).toBe('OPEN_LONG')
    expect(activities.activities[1].legs).toEqual([])
    expect(backendFetch).not.toHaveBeenCalled()
  })

  it('blocks all demo mutations and clears the session on Exit Demo', async () => {
    enableDemoMode()
    const backendFetch = vi.fn()
    vi.stubGlobal('fetch', backendFetch)

    await expect(createConnection({ provider: 'SOLANA', walletAddress: 'ignored' }))
      .rejects.toBeInstanceOf(ConnectionsApiError)
    await logout()

    expect(window.sessionStorage.getItem(demoModeStorageKey)).toBeNull()
    expect(backendFetch).not.toHaveBeenCalled()
  })
})
