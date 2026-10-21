import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: 'tests',
  workers: 1,
  retries: 0,
  use: {
    baseURL: 'http://127.0.0.1:8080',
    viewport: { width: 1440, height: 1000 },
    browserName: 'chromium',
    launchOptions: {
      executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium',
      args: ['--no-sandbox'],
    },
    trace: 'off',
  },
  reporter: [['list']],
  outputDir: 'test-results',
})
