import { DesignSuggestion, LetterheadElement, LetterheadProject } from '../types/letterhead';
import { rectsOverlap, roundMm } from './geometry';

export function analyzeLetterheadDesign(project: LetterheadProject): DesignSuggestion[] {
  const suggestions: DesignSuggestion[] = [];
  const { pageSettings, elements } = project;
  const { margins, widthMm, heightMm } = pageSettings;
  const contentWidth = widthMm - margins.left - margins.right;
  const pageZero = elements.filter((e) => e.pageIndex === 0 && e.visible);

  // 1. Check Company Title completeness & hierarchy
  const titleEl = pageZero.find((e) => e.role === 'company-title');
  const logoEl = pageZero.find((e) => e.role === 'logo');
  if (titleEl) {
    const text = (titleEl.content || '').trim();
    const isShortBrand = text.toLowerCase() === 'grashy';
    const fontSize = titleEl.textStyle?.fontSize ?? 12;
    const isNearRightEdge = titleEl.x + titleEl.width > widthMm - margins.right + 1;

    if (isShortBrand || isNearRightEdge || fontSize < 18) {
      suggestions.push({
        id: 'audit-company-title',
        category: 'Logo & Header',
        severity: 'high',
        title: isShortBrand
          ? 'Company title "Grashy" is truncated and crowded against the right edge'
          : 'Company title sits outside safe right margin or lacks visual hierarchy',
        explanation:
          'In the uploaded PDF, the header displays only "Grashy" pushed tightly to the right border. Expanding to "Grashy Caterers", aligning flush with the 15mm right margin, and balancing scale with the logo creates an authoritative brand lockup.',
        affectedElementIds: [titleEl.id],
        fixLabel: 'Align & Elevate Title',
        canAutoFix: true,
        applyFix: (p) => ({
          ...p,
          elements: p.elements.map((el) =>
            el.id === titleEl.id
              ? {
                  ...el,
                  x: margins.left + 52,
                  y: 13,
                  width: contentWidth - 52,
                  height: 11,
                  content: el.content?.trim().toLowerCase() === 'grashy' ? 'Grashy Caterers' : el.content,
                  textStyle: el.textStyle
                    ? { ...el.textStyle, fontSize: 22, fontWeight: 700, textAlign: 'right' }
                    : undefined,
                }
              : el
          ),
        }),
      });
    }
  }

  // 2. Check Contact Information clipping (e.g. "Tel: 0727 4")
  const contactEl = pageZero.find((e) => e.role === 'contact');
  if (contactEl) {
    const txt = (contactEl.content || '').trim();
    const looksTruncated = /Tel:\s*0\d{3}\s*\d?$/i.test(txt);
    const clipsMargin = contactEl.x + contactEl.width > widthMm - margins.right + 1;

    if (looksTruncated || clipsMargin) {
      suggestions.push({
        id: 'audit-contact-clipped',
        category: 'Contact & Footer',
        severity: 'high',
        title: looksTruncated
          ? 'Telephone number ("Tel: 0727 4") appears cut off at the right margin'
          : 'Contact block extends past safe printable right margin',
        explanation:
          'The original PDF clips the telephone line right after "0727 4" due to a narrow right-edge text box. Widen the contact container inside the 15mm right margin and add a structured phone/email template so numbers never clip.',
        affectedElementIds: [contactEl.id],
        fixLabel: 'Fix Contact Box Bounds',
        canAutoFix: true,
        applyFix: (p) => ({
          ...p,
          elements: p.elements.map((el) =>
            el.id === contactEl.id
              ? {
                  ...el,
                  x: margins.left + 60,
                  y: 25,
                  width: contentWidth - 60,
                  height: 14,
                  content:
                    el.content?.trim() === 'Tel: 0727 4'
                      ? 'Tel: 0727 4XX XXX  |  Karen, Nairobi\nEmail: info@grashycaterers.co.ke'
                      : el.content,
                  textStyle: el.textStyle
                    ? { ...el.textStyle, fontSize: 9.5, fontWeight: 500, lineHeight: 1.45, color: '#334155', textAlign: 'right' }
                    : undefined,
                }
              : el
          ),
        }),
      });
    }
  }

  // 3. Check Header Separator (dotted ellipsis string vs clean vector line)
  const sepEl = pageZero.find((e) => e.role === 'separator');
  if (sepEl && sepEl.type === 'text' && /\.{6,}|…{3,}/.test(sepEl.content || '')) {
    suggestions.push({
      id: 'audit-dotted-separator',
      category: 'Logo & Header',
      severity: 'high',
      title: 'Header divider uses typed period characters instead of a crisp rule',
      explanation:
        'Typed dots ("...........") stop awkwardly short of the right margin and render unevenly across printers and DOCX/PDF exports. Replace with a full-width 1.5pt brand-orange hairline rule aligned to your 15mm page margins.',
      affectedElementIds: [sepEl.id],
      fixLabel: 'Convert to Crisp Rule',
      canAutoFix: true,
      applyFix: (p) => ({
        ...p,
        elements: p.elements.map((el) =>
          el.id === sepEl.id
            ? {
                ...el,
                type: 'line',
                name: 'Header Accent Rule',
                x: margins.left,
                y: 44,
                width: contentWidth,
                height: 2,
                content: undefined,
                textStyle: undefined,
                shapeStyle: {
                  fill: 'transparent',
                  stroke: '#C45911',
                  strokeWidth: 1.5,
                  borderRadius: 0,
                  lineStyle: 'solid',
                },
              }
            : el
        ),
      }),
    });
  }

  // 4. Check Logo alignment & proportion
  if (logoEl && (logoEl.x > margins.left + 2 || logoEl.width > 46)) {
    suggestions.push({
      id: 'audit-logo-margin',
      category: 'Margins & Alignment',
      severity: 'medium',
      title: 'Logo sits at x=24mm instead of aligning with left document margin',
      explanation:
        'Aligning the Grashy Caterers emblem flush with the 15mm left margin (and scaling slightly to 42mm wide) creates an immediate vertical grid line down the page shared with event metadata, body copy, and payment details.',
      affectedElementIds: [logoEl.id],
      fixLabel: 'Snap Logo to Margin',
      canAutoFix: true,
      applyFix: (p) => ({
        ...p,
        elements: p.elements.map((el) =>
          el.id === logoEl.id ? { ...el, x: margins.left, y: 12, width: 42, height: 28.5 } : el
        ),
      }),
    });
  }

  // 5. Check Typography & Text Spacing quirks (e.g. "Tea,Chocolate", "No of guests:35", emoji bullets)
  const metaEl = pageZero.find((e) => e.role === 'event-meta');
  const bodyEl = pageZero.find((e) => e.role === 'body');
  const textIssueIds: string[] = [];
  if (metaEl && /:[A-Za-z0-9]/.test(metaEl.content || '')) textIssueIds.push(metaEl.id);
  if (bodyEl && (/Tea,Chocolate|💫|\n{3,}/.test(bodyEl.content || ''))) textIssueIds.push(bodyEl.id);

  if (textIssueIds.length > 0) {
    suggestions.push({
      id: 'audit-typography-cleanup',
      category: 'Typography',
      severity: 'medium',
      title: 'Inconsistent spacing around colons, commas, and bullet symbols',
      explanation:
        'Detected tight punctuation ("No of guests:35", "Tea,Chocolate"), raw emoji bullets (💫), and excess blank lines. Standardizing spacing and bullets improves readability and prints cleanly in DOCX/PDF.',
      affectedElementIds: textIssueIds,
      fixLabel: 'Clean Punctuation & Bullets',
      canAutoFix: true,
      applyFix: (p) => ({
        ...p,
        elements: p.elements.map((el) => {
          if (el.id === metaEl?.id) {
            return {
              ...el,
              x: margins.left,
              width: contentWidth,
              content: (el.content || '')
                .replace(/:[ \t]*/g, ': ')
                .replace(/[ \t]+\n/g, '\n')
                .replace(/^No of guests/im, 'No. of Guests'),
              textStyle: el.textStyle
                ? { ...el.textStyle, lineHeight: 1.55, color: '#9A3412' }
                : undefined,
            };
          }
          if (el.id === bodyEl?.id) {
            return {
              ...el,
              x: margins.left,
              width: contentWidth,
              content: (el.content || '')
                .replace(/💫\s*/g, '•  ')
                .replace(/Tea,Chocolate/g, 'Tea & Hot Chocolate')
                .replace(/Mandazi\/Bread/g, 'Mandazi / Fresh Bread')
                .replace(/\n{3,}/g, '\n\n'),
              textStyle: el.textStyle
                ? { ...el.textStyle, lineHeight: 1.55, color: '#1E293B' }
                : undefined,
            };
          }
          return el;
        }),
      }),
    });
  }

  // 6. Check Payment Footer composition (black line cutting across OR + stacked layout)
  const bankEl = pageZero.find((e) => e.role === 'payment-bank');
  const mpesaEl = pageZero.find((e) => e.role === 'payment-mpesa');
  const payDivEl = pageZero.find((e) => e.role === 'payment-divider');
  if (bankEl && mpesaEl && Math.abs(bankEl.x - mpesaEl.x) < 10) {
    suggestions.push({
      id: 'audit-payment-columns',
      category: 'Contact & Footer',
      severity: 'high',
      title: 'Payment instructions are stacked unevenly with a hard line cutting across',
      explanation:
        'The NCBA bank block and M-Pesa Till block currently stack vertically with a heavy black rule bisecting the text. Placing them side-by-side in a balanced 2-column payment footer recovers ~25mm of document breathing room.',
      affectedElementIds: [bankEl.id, mpesaEl.id, ...(payDivEl ? [payDivEl.id] : [])],
      fixLabel: 'Arrange 2-Column Footer',
      canAutoFix: true,
      applyFix: (p) => {
        const w = p.pageSettings.widthMm - p.pageSettings.margins.left - p.pageSettings.margins.right;
        const lx = p.pageSettings.margins.left;
        return {
          ...p,
          elements: p.elements.map((el) => {
            if (el.id === bankEl.id) {
              return {
                ...el,
                x: lx,
                y: 226,
                width: (w - 8) * 0.56,
                height: 28,
                content: (el.content || '').replace(/Account no:/i, 'Account No: '),
                textStyle: el.textStyle
                  ? { ...el.textStyle, fontSize: 9.5, lineHeight: 1.5, paddingTop: 2.5, paddingRight: 3, paddingBottom: 2.5, paddingLeft: 3 }
                  : undefined,
                shapeStyle: { fill: '#F8FAFC', stroke: '#E2E8F0', strokeWidth: 0.75, borderRadius: 2, lineStyle: 'solid' },
              };
            }
            if (el.id === mpesaEl.id) {
              return {
                ...el,
                x: lx + (w - 8) * 0.56 + 8,
                y: 226,
                width: (w - 8) * 0.44,
                height: 28,
                content: (el.content || '').replace(/^\s*OR\s*\n?/i, '').replace(/Mpesa\s*:\s*/i, 'M-Pesa: '),
                textStyle: el.textStyle
                  ? { ...el.textStyle, fontSize: 9.5, lineHeight: 1.5, paddingTop: 2.5, paddingRight: 3, paddingBottom: 2.5, paddingLeft: 3 }
                  : undefined,
                shapeStyle: { fill: '#F8FAFC', stroke: '#E2E8F0', strokeWidth: 0.75, borderRadius: 2, lineStyle: 'solid' },
              };
            }
            if (payDivEl && el.id === payDivEl.id) {
              return {
                ...el,
                x: lx,
                y: 222,
                width: w,
                shapeStyle: { fill: 'transparent', stroke: '#CBD5E1', strokeWidth: 0.75, borderRadius: 0, lineStyle: 'solid' },
              };
            }
            return el;
          }),
        };
      },
    });
  }

  // 7. Check Footer Wave edge-to-edge bleed
  const waveEl = pageZero.find((e) => e.role === 'footer-wave');
  if (waveEl && (waveEl.x > 1 || waveEl.width < widthMm - 2)) {
    suggestions.push({
      id: 'audit-footer-wave-bleed',
      category: 'Spacing & Balance',
      severity: 'medium',
      title: 'Bottom brand wave stops short of the A4 left and right paper edges',
      explanation:
        'Full-bleed architectural waves look intentional when anchored edge-to-edge (0mm to 210mm) at the foot of the page rather than floating 8mm inset.',
      affectedElementIds: [waveEl.id],
      fixLabel: 'Stretch Wave Full-Bleed',
      canAutoFix: true,
      applyFix: (p) => ({
        ...p,
        elements: p.elements.map((el) =>
          el.id === waveEl.id
            ? { ...el, x: 0, y: roundMm(heightMm - 20), width: widthMm, height: 20 }
            : el
        ),
      }),
    });
  }

  // 8. Check element overlaps on active page
  const contentEls = pageZero.filter(
    (e) => e.type !== 'line' && e.type !== 'shape' && e.role !== 'footer-wave'
  );
  for (let i = 0; i < contentEls.length; i++) {
    for (let j = i + 1; j < contentEls.length; j++) {
      const a = contentEls[i];
      const b = contentEls[j];
      if (rectsOverlap(a, b, -1)) {
        suggestions.push({
          id: `audit-overlap-${a.id}-${b.id}`,
          category: 'Margins & Alignment',
          severity: 'high',
          title: `"${a.name}" overlaps "${b.name}"`,
          explanation:
            'Overlapping text or graphic frames can hide information when printed or exported to PDF/DOCX. Separate vertical bounds cleanly.',
          affectedElementIds: [a.id, b.id],
          fixLabel: 'Separate Elements',
          canAutoFix: true,
          applyFix: (p) => {
            const first = a.y <= b.y ? a : b;
            const second = a.y <= b.y ? b : a;
            return {
              ...p,
              elements: p.elements.map((el) =>
                el.id === second.id ? { ...el, y: roundMm(first.y + first.height + 4) } : el
              ),
            };
          },
        });
        break;
      }
    }
  }

  // 9. Check white space balance between body and footer
  if (bodyEl) {
    const closingEl = pageZero.find((e) => e.role === 'closing');
    if (closingEl) {
      const gap = closingEl.y - (bodyEl.y + bodyEl.height);
      if (gap > 55) {
        suggestions.push({
          id: 'audit-whitespace-gap',
          category: 'Spacing & Balance',
          severity: 'low',
          title: `Large ${Math.round(gap)}mm empty void between quotation body and footer`,
          explanation:
            'Expanding the body frame height and adding structured vertical rhythm prevents short quotes (like Breakfast) from leaving an awkward mid-page void.',
          affectedElementIds: [bodyEl.id],
          fixLabel: 'Balance Body Container',
          canAutoFix: true,
          applyFix: (p) => ({
            ...p,
            elements: p.elements.map((el) =>
              el.id === bodyEl.id
                ? { ...el, y: 82, height: Math.max(el.height, closingEl.y - 82 - 12) }
                : el
            ),
          }),
        });
      }
    }
  }

  return suggestions;
}

export function computeLayoutHealthSummary(suggestions: DesignSuggestion[]): {
  highCount: number;
  mediumCount: number;
  lowCount: number;
  statusText: string;
} {
  const highCount = suggestions.filter((s) => s.severity === 'high').length;
  const mediumCount = suggestions.filter((s) => s.severity === 'medium').length;
  const lowCount = suggestions.filter((s) => s.severity === 'low').length;

  let statusText = 'Layout is balanced and print-ready';
  if (highCount > 0) {
    statusText = `${highCount} high-priority layout issue${highCount > 1 ? 's' : ''} detected`;
  } else if (mediumCount > 0) {
    statusText = `${mediumCount} refinement${mediumCount > 1 ? 's' : ''} recommended`;
  }

  return { highCount, mediumCount, lowCount, statusText };
}

export function getElementBoundsSummary(el: LetterheadElement): string {
  return `X ${el.x.toFixed(1)}mm · Y ${el.y.toFixed(1)}mm · ${el.width.toFixed(1)}×${el.height.toFixed(1)}mm`;
}
