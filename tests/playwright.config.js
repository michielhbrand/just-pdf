// @ts-check
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: '.',
  testMatch: '*.spec.js',
  timeout: 60_000,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],

  use: {
    // Simulate a typical Android phone screen in CSS pixels.
    // deviceScaleFactor=3 means 1 CSS px = 3 physical px, matching high-DPI Android.
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 3,

    // Headless by default; use --headed flag or npm run test:headed to watch.
    headless: true,

    // Base URL of the HTTP server serving the assets.
    baseURL: 'http://localhost:8080',

    // Allow ES module imports (pdf.mjs uses import()).
    bypassCSP: true,
  },

  projects: [
    {
      name: 'chromium',
      // No channel override — use the Playwright-installed Chromium binary.
    },
  ],
});
