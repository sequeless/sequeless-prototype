import { existsSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

// Mirrors where playwright-core looks (computeDefaultCacheDirectory + 'ms-playwright'), so the check reads the same place.
function defaultBrowsersDir(env: NodeJS.ProcessEnv): string {
  if (process.platform === 'darwin') return join(homedir(), 'Library', 'Caches', 'ms-playwright');
  if (process.platform === 'win32') {
    return join(env['LOCALAPPDATA'] ?? join(homedir(), 'AppData', 'Local'), 'ms-playwright');
  }
  return join(env['XDG_CACHE_HOME'] ?? join(homedir(), '.cache'), 'ms-playwright');
}

// Fails fast with a clear message instead of a launch error. Browsers are installed out of band; this never downloads.
export function assertBrowsersInstalled(
  env: NodeJS.ProcessEnv = process.env,
  exists: (p: string) => boolean = existsSync,
): void {
  const dir = env['PLAYWRIGHT_BROWSERS_PATH'] ?? defaultBrowsersDir(env);
  const installed = exists(dir) && readdirSync(dir).some((entry) => /^chromium-\d+$/.test(entry));
  if (!installed) {
    throw new Error(`Playwright browsers not found at ${dir}. Install them out of band; this config never downloads.`);
  }
}
