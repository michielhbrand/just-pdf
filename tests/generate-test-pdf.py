#!/usr/bin/env python3
"""
Generate a test PDF with known text content for Playwright highlight alignment tests.

The PDF contains:
  - A left-aligned paragraph with short and long words
  - A justified paragraph (the hard case for scaleX alignment)
  - Bold text
  - Mixed line lengths

Output: ../app/src/main/assets/test.pdf
"""

import os
from fpdf import FPDF

OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'test.pdf')

pdf = FPDF(orientation='P', unit='pt', format='A4')
pdf.set_margins(60, 60, 60)
pdf.add_page()

# ── Left-aligned paragraph ────────────────────────────────────────────────
pdf.set_font('Helvetica', '', 12)
pdf.set_x(60)
pdf.cell(0, 20, 'Left-aligned text', ln=True)

left_text = (
    'The quick brown fox jumps over the lazy dog. '
    'Pack my box with five dozen liquor jugs. '
    'How vexingly quick daft zebras jump. '
    'The five boxing wizards jump quickly.'
)
pdf.set_font('Helvetica', '', 11)
pdf.multi_cell(0, 16, left_text, align='L')
pdf.ln(10)

# ── Justified paragraph ───────────────────────────────────────────────────
pdf.set_font('Helvetica', '', 12)
pdf.cell(0, 20, 'Justified text (hard case for scaleX)', ln=True)

justified_text = (
    'Missional living begins with the gospel coming to us. '
    'Abraham believed God and it was credited to him as righteousness. '
    'The primary purpose of the church is to spread the whole gospel '
    'to the whole world through the whole church. '
    'God pours out His Spirit on all who believe in Jesus.'
)
pdf.set_font('Helvetica', '', 11)
pdf.multi_cell(0, 16, justified_text, align='J')
pdf.ln(10)

# ── Bold text ─────────────────────────────────────────────────────────────
pdf.set_font('Helvetica', 'B', 12)
pdf.cell(0, 20, 'Bold heading text', ln=True)

pdf.set_font('Helvetica', 'B', 11)
bold_text = (
    'The Big Story of God. Missional opportunities locally. '
    'Missions Globally. Primary calling.'
)
pdf.multi_cell(0, 16, bold_text, align='L')
pdf.ln(10)

# ── Mixed sizes ───────────────────────────────────────────────────────────
pdf.set_font('Helvetica', '', 12)
pdf.cell(0, 20, 'Mixed font sizes', ln=True)

pdf.set_font('Helvetica', '', 9)
pdf.multi_cell(0, 14, 'Small text: the quick brown fox jumps over the lazy dog.', align='L')
pdf.ln(4)
pdf.set_font('Helvetica', '', 13)
pdf.multi_cell(0, 18, 'Larger text: Abraham, God, Spirit, Jesus.', align='L')

pdf.output(OUT)
print(f'Generated: {os.path.abspath(OUT)}')
