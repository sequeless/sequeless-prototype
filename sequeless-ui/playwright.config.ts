import { defineConfig, devices } from '@playwright/test';
import { assertBrowsersInstalled } from './e2e/support/browser-check';
import { e2eEnv } from './e2e/support/test';

// E2E_SKIP_BROWSER_CHECK=1 is for `playwright test --list` only; real runs need the browsers pre-installed.
if (process.env['E2E_SKIP_BROWSER_CHECK'] !== '1') {
  assertBrowsersInstalled();
}

export default defineConfig({
  testDir: 'e2e',
  retries: 0,
  use: { baseURL: e2eEnv().baseUrl },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
