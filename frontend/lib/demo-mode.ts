export const demoModeStorageKey = 'crypto-portfolio-hub:demo-mode'
let inMemoryDemoMode = false

export function isDemoMode(): boolean {
  if (typeof window === 'undefined') return false
  try {
    return window.sessionStorage.getItem(demoModeStorageKey) === 'enabled'
  } catch {
    return inMemoryDemoMode
  }
}

export function enableDemoMode(): void {
  if (typeof window === 'undefined') return
  inMemoryDemoMode = true
  try {
    window.sessionStorage.setItem(demoModeStorageKey, 'enabled')
  } catch {
    // The in-memory flag still supports this browser session when storage is blocked.
  }
}

export function disableDemoMode(): void {
  if (typeof window === 'undefined') return
  inMemoryDemoMode = false
  try {
    window.sessionStorage.removeItem(demoModeStorageKey)
  } catch {
    // Clearing the in-memory flag still exits demo mode when storage is blocked.
  }
}
