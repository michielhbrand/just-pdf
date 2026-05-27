// @ts-check
/**
 * Playwright test: selection highlight alignment
 *
 * For every <span> in the .textLayer:
 *   1. Select the span's full text via Range API
 *   2. Dispatch selectionchange to trigger drawHighlight()
 *   3. Read the .highlightLayer canvas pixel data
 *   4. Find the leftmost and rightmost blue pixels drawn
 *   5. Compare to span.getBoundingClientRect() (relative to page wrapper)
 *   6. Assert both edges are within TOLERANCE px
 *
 * TOLERANCE is intentionally generous (8px) to account for:
 *   - Sub-pixel rounding
 *   - Desktop Chromium vs Android WebView font metric differences
 *   - Spans that are only 1-2 chars wide
 *
 * A FAIL means the highlight is clearly wrong (e.g. extends into the margin,
 * covers the wrong word, or doesn't appear at all).
 */

const { test, expect } = require('@playwright/test');

const VIEWER_URL = '/viewer.html';
const PDF_URL    = 'http://localhost:8080/example-pdf.pdf';
const TOLERANCE  = 8; // px — acceptable edge misalignment

// Minimum span width to test (skip very narrow spans like punctuation)
const MIN_SPAN_WIDTH = 4;

test.describe('Selection highlight alignment', () => {

  test.beforeEach(async ({ page }) => {
    // Mock the Android bridge so the viewer doesn't hang waiting for it.
    await page.addInitScript(() => {
      window.Android = {
        onViewerReady:  () => {},
        onPdfLoaded:    (n) => console.log(`PDF loaded: ${n} pages`),
        onPdfError:     (e) => console.error(`PDF error: ${e}`),
      };
    });

    await page.goto(VIEWER_URL);

    // Wait for the viewer script to call onViewerReady (i.e. finish initialising).
    await page.waitForFunction(() => typeof window.loadPdf === 'function');

    // Load the test PDF and wait for at least one text layer span to appear.
    await page.evaluate((url) => window.loadPdf(url), PDF_URL);
    await page.waitForSelector('.textLayer span', { timeout: 30_000 });

    // Extra settle time for all pages to render.
    await page.waitForTimeout(1000);
  });

  test('every span highlight aligns with visual text bounds', async ({ page }) => {
    const results = await page.evaluate(async (tolerance) => {
      const HIGHLIGHT_COLOR_R = 0;
      const HIGHLIGHT_COLOR_G = 120;
      const HIGHLIGHT_COLOR_B = 255;
      const ALPHA_THRESHOLD   = 30; // minimum alpha to count as "blue pixel"

      const failures = [];
      const passes   = [];

      const spans = Array.from(document.querySelectorAll('.textLayer span'));

      for (const span of spans) {
        const text = span.textContent || '';
        if (text.trim() === '') continue;

        // Get the page wrapper and its highlight canvas.
        const wrapper  = span.closest('.page-wrapper');
        if (!wrapper) continue;
        const hlCanvas = wrapper.querySelector('.highlightLayer');
        if (!hlCanvas) continue;
        const ctx      = hlCanvas.getContext('2d');

        // Clear the canvas before each test.
        ctx.clearRect(0, 0, hlCanvas.width, hlCanvas.height);

        // Select the full span.
        const range = document.createRange();
        range.selectNodeContents(span);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);

        // Trigger drawHighlight().
        document.dispatchEvent(new Event('selectionchange'));

        // Allow a microtask to flush.
        await new Promise(r => setTimeout(r, 0));

        // Read canvas pixels.
        const imgData = ctx.getImageData(0, 0, hlCanvas.width, hlCanvas.height);
        const data    = imgData.data;
        const W       = hlCanvas.width;
        const H       = hlCanvas.height;

        // Find bounding box of blue pixels on the canvas.
        let minX = Infinity, maxX = -Infinity;
        let minY = Infinity, maxY = -Infinity;
        let bluePixelCount = 0;

        for (let y = 0; y < H; y++) {
          for (let x = 0; x < W; x++) {
            const i = (y * W + x) * 4;
            const r = data[i], g = data[i+1], b = data[i+2], a = data[i+3];
            // Check for our highlight colour (with some tolerance for blending).
            if (a > ALPHA_THRESHOLD &&
                Math.abs(r - HIGHLIGHT_COLOR_R) < 80 &&
                Math.abs(g - HIGHLIGHT_COLOR_G) < 80 &&
                Math.abs(b - HIGHLIGHT_COLOR_B) < 80) {
              if (x < minX) minX = x;
              if (x > maxX) maxX = x;
              if (y < minY) minY = y;
              if (y > maxY) maxY = y;
              bluePixelCount++;
            }
          }
        }

        // Get the span's visual rect relative to the wrapper (= canvas coords).
        const wrapperRect = wrapper.getBoundingClientRect();
        const spanRect    = span.getBoundingClientRect();
        const expectedLeft  = spanRect.left  - wrapperRect.left;
        const expectedRight = spanRect.right - wrapperRect.left;
        const spanWidth     = expectedRight - expectedLeft;

        // Skip very narrow spans (punctuation, single chars) — too noisy.
        if (spanWidth < 4) continue;

        if (bluePixelCount === 0) {
          failures.push({
            text,
            issue: 'NO_PIXELS',
            expectedLeft, expectedRight, spanWidth,
          });
          continue;
        }

        const leftOvershoot  = expectedLeft  - minX;  // positive = highlight starts too early
        const rightOvershoot = maxX - expectedRight;  // positive = highlight extends too far right
        const leftUndershoot = minX - expectedLeft;   // positive = highlight starts too late
        const rightUndershoot = expectedRight - maxX; // positive = highlight ends too early

        const ok = (
          Math.abs(leftOvershoot)   <= tolerance &&
          Math.abs(rightOvershoot)  <= tolerance &&
          Math.abs(leftUndershoot)  <= tolerance &&
          Math.abs(rightUndershoot) <= tolerance
        );

        const result = {
          text: text.slice(0, 30),
          expectedLeft:  Math.round(expectedLeft),
          expectedRight: Math.round(expectedRight),
          actualLeft:    minX,
          actualRight:   maxX,
          leftDelta:     Math.round(minX - expectedLeft),   // + = starts too late
          rightDelta:    Math.round(maxX - expectedRight),  // + = ends too late (overshoot)
          spanWidth:     Math.round(spanWidth),
          bluePixelCount,
        };

        if (ok) {
          passes.push(result);
        } else {
          failures.push({ ...result, issue: 'MISALIGNED' });
        }
      }

      // Clear selection.
      window.getSelection().removeAllRanges();
      document.dispatchEvent(new Event('selectionchange'));

      return { passes, failures, total: passes.length + failures.length };
    }, TOLERANCE);

    // Print summary.
    console.log(`\nHighlight alignment: ${results.passes.length}/${results.total} passed`);

    if (results.failures.length > 0) {
      console.log('\nFAILURES:');
      for (const f of results.failures) {
        if (f.issue === 'NO_PIXELS') {
          console.log(`  [NO_PIXELS] "${f.text}" (expected left=${f.expectedLeft} right=${f.expectedRight})`);
        } else {
          console.log(
            `  [MISALIGNED] "${f.text}" ` +
            `expected=[${f.expectedLeft},${f.expectedRight}] ` +
            `actual=[${f.actualLeft},${f.actualRight}] ` +
            `leftDelta=${f.leftDelta > 0 ? '+' : ''}${f.leftDelta}px ` +
            `rightDelta=${f.rightDelta > 0 ? '+' : ''}${f.rightDelta}px`
          );
        }
      }
    }

    // Fail the test if any spans are misaligned.
    expect(results.failures, `${results.failures.length} spans misaligned`).toHaveLength(0);
  });

});
