import { jsPDF } from 'jspdf';
import {
  AlignmentType,
  BorderStyle,
  Document,
  ImageRun,
  Packer,
  Paragraph,
  ShadingType,
  Table,
  TableCell,
  TableRow,
  TextRun,
  WidthType,
} from 'docx';
import JSZip from 'jszip';
import * as pdfjsLib from 'pdfjs-dist';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import { LetterheadElement, LetterheadProject } from '../types/letterhead';
import { PT_TO_MM, roundMm } from './geometry';
import { saveOrShareBlobOnDevice } from './androidBridge';

// Use locally bundled pdf.js worker via Vite so PDF import works without external CDN calls
if (typeof window !== 'undefined' && pdfjsLib.GlobalWorkerOptions) {
  pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;
}

interface CachedImageData {
  dataUrl: string;
  width: number;
  height: number;
}

const preloadedImageCache = new Map<string, CachedImageData>();

async function loadImageAsDataUrl(src: string): Promise<CachedImageData | null> {
  if (src.startsWith('data:')) {
    // Allow direct canvas sizing below without caching huge arbitrary data URLs permanently
  } else {
    const cached = preloadedImageCache.get(src);
    if (cached) return cached;
  }

  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = img.naturalWidth || 400;
        canvas.height = img.naturalHeight || 200;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(null);
          return;
        }
        ctx.drawImage(img, 0, 0);
        const result: CachedImageData = {
          dataUrl: canvas.toDataURL('image/png'),
          width: canvas.width,
          height: canvas.height,
        };
        if (!src.startsWith('data:')) {
          preloadedImageCache.set(src, result);
        }
        resolve(result);
      } catch {
        resolve(null);
      }
    };
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

if (typeof window !== 'undefined' && typeof Image !== 'undefined') {
  void loadImageAsDataUrl('/assets/grashy-logo.png');
  void loadImageAsDataUrl('/assets/grashy-footer-wave.png');
}

async function loadImageAsUint8Array(src: string): Promise<{ bytes: Uint8Array; width: number; height: number } | null> {
  const loaded = await loadImageAsDataUrl(src);
  if (!loaded) return null;
  const base64 = loaded.dataUrl.split(',')[1];
  const bin = atob(base64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    bytes[i] = bin.charCodeAt(i);
  }
  return { bytes, width: loaded.width, height: loaded.height };
}

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace('#', '').trim();
  if (clean.length === 3) {
    const r = parseInt(clean[0] + clean[0], 16);
    const g = parseInt(clean[1] + clean[1], 16);
    const b = parseInt(clean[2] + clean[2], 16);
    return [r, g, b];
  }
  if (clean.length >= 6) {
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    return [Number.isNaN(r) ? 0 : r, Number.isNaN(g) ? 0 : g, Number.isNaN(b) ? 0 : b];
  }
  return [15, 23, 42];
}

function normalizeDocxHex(hex: string | undefined, fallback = '1E293B'): string {
  const clean = (hex || '').replace('#', '').trim();
  if (/^[0-9a-fA-F]{3}$/.test(clean)) {
    return `${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}`.toUpperCase();
  }
  if (/^[0-9a-fA-F]{6}$/.test(clean)) {
    return clean.toUpperCase();
  }
  return fallback;
}

function mapPdfFont(fontFamily: string): 'helvetica' | 'times' | 'courier' {
  const lower = fontFamily.toLowerCase();
  if (lower.includes('playfair') || lower.includes('serif') || lower.includes('lora')) {
    return 'times';
  }
  if (lower.includes('mono') || lower.includes('courier')) {
    return 'courier';
  }
  return 'helvetica';
}

export async function exportToPdf(
  project: LetterheadProject
): Promise<'native-shared' | 'web-downloaded'> {
  const pdf = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4',
    compress: true,
  });

  for (let pageIdx = 0; pageIdx < project.pageCount; pageIdx++) {
    if (pageIdx > 0) pdf.addPage('a4', 'portrait');

    const pageElements = project.elements
      .filter((el) => el.pageIndex === pageIdx && el.visible)
      .sort((a, b) => a.zIndex - b.zIndex);

    for (const el of pageElements) {
      const pdfAny = pdf as unknown as {
        GState?: new (opts: { opacity: number }) => unknown;
        setGState?: (gs: unknown) => void;
      };
      if (el.opacity < 1 && pdfAny.GState && pdfAny.setGState) {
        pdfAny.setGState(new pdfAny.GState({ opacity: el.opacity }));
      }

      if (el.type === 'image' && el.imageSrc) {
        const imgData = await loadImageAsDataUrl(el.imageSrc);
        if (imgData) {
          pdf.addImage(imgData.dataUrl, 'PNG', el.x, el.y, el.width, el.height, undefined, 'FAST');
        }
      } else if (el.type === 'line' && el.shapeStyle) {
        if (el.shapeStyle.stroke !== 'transparent' && el.shapeStyle.strokeWidth > 0) {
          const [r, g, b] = hexToRgb(el.shapeStyle.stroke || '#C45911');
          pdf.setDrawColor(r, g, b);
          pdf.setLineWidth(Math.max(0.2, el.shapeStyle.strokeWidth * PT_TO_MM));
          if (el.shapeStyle.lineStyle === 'dashed') {
            pdf.setLineDashPattern([3, 2], 0);
          } else if (el.shapeStyle.lineStyle === 'dotted') {
            pdf.setLineDashPattern([0.8, 1.4], 0);
          } else {
            pdf.setLineDashPattern([], 0);
          }
          const midY = el.y + el.height / 2;
          pdf.line(el.x, midY, el.x + el.width, midY);
          pdf.setLineDashPattern([], 0);
        }
      } else if (el.type === 'shape' && el.shapeStyle) {
        const hasFill = el.shapeStyle.fill && el.shapeStyle.fill !== 'transparent';
        const hasStroke =
          el.shapeStyle.stroke &&
          el.shapeStyle.stroke !== 'transparent' &&
          el.shapeStyle.strokeWidth > 0;
        if (hasFill || hasStroke) {
          if (hasFill) {
            const [r, g, b] = hexToRgb(el.shapeStyle.fill);
            pdf.setFillColor(r, g, b);
          }
          if (hasStroke) {
            const [r, g, b] = hexToRgb(el.shapeStyle.stroke);
            pdf.setDrawColor(r, g, b);
            pdf.setLineWidth(el.shapeStyle.strokeWidth * PT_TO_MM);
          }
          const style = hasFill && hasStroke ? 'FD' : hasFill ? 'F' : 'S';
          if (el.shapeStyle.borderRadius > 0) {
            pdf.roundedRect(
              el.x,
              el.y,
              el.width,
              el.height,
              el.shapeStyle.borderRadius,
              el.shapeStyle.borderRadius,
              style
            );
          } else {
            pdf.rect(el.x, el.y, el.width, el.height, style);
          }
        }
      } else if (el.type === 'text' && el.textStyle) {
        // Optional background box on text element
        if (el.shapeStyle) {
          const hasFill = Boolean(el.shapeStyle.fill && el.shapeStyle.fill !== 'transparent');
          const hasStroke = Boolean(
            el.shapeStyle.stroke &&
              el.shapeStyle.stroke !== 'transparent' &&
              el.shapeStyle.strokeWidth > 0
          );
          if (hasFill || hasStroke) {
            if (hasFill) {
              const [fr, fg, fb] = hexToRgb(el.shapeStyle.fill);
              pdf.setFillColor(fr, fg, fb);
            }
            if (hasStroke) {
              const [sr, sg, sb] = hexToRgb(el.shapeStyle.stroke);
              pdf.setDrawColor(sr, sg, sb);
              pdf.setLineWidth(el.shapeStyle.strokeWidth * PT_TO_MM);
            }
            const style = hasFill && hasStroke ? 'FD' : hasFill ? 'F' : 'S';
            if (el.shapeStyle.borderRadius > 0) {
              pdf.roundedRect(
                el.x,
                el.y,
                el.width,
                el.height,
                el.shapeStyle.borderRadius,
                el.shapeStyle.borderRadius,
                style
              );
            } else {
              pdf.rect(el.x, el.y, el.width, el.height, style);
            }
          }
        }

        const ts = el.textStyle;
        const pdfFont = mapPdfFont(ts.fontFamily);
        const fontStyle =
          ts.fontWeight >= 600 && ts.fontStyle === 'italic'
            ? 'bolditalic'
            : ts.fontWeight >= 600
              ? 'bold'
              : ts.fontStyle === 'italic'
                ? 'italic'
                : 'normal';

        pdf.setFont(pdfFont, fontStyle);
        pdf.setFontSize(ts.fontSize);
        const [tr, tg, tb] = hexToRgb(ts.color);
        pdf.setTextColor(tr, tg, tb);

        const innerX = el.x + ts.paddingLeft;
        const innerW = Math.max(8, el.width - ts.paddingLeft - ts.paddingRight);
        const lineAdvanceMm = ts.fontSize * PT_TO_MM * ts.lineHeight;
        let cursorY = el.y + ts.paddingTop + ts.fontSize * PT_TO_MM * 0.85;

        const paragraphs = (el.content || '').split('\n');
        for (const para of paragraphs) {
          if (para.trim() === '') {
            cursorY += lineAdvanceMm * 0.65;
            continue;
          }
          // Replace emoji stars with clean bullet symbol for PDF built-in fonts
          const pdfSafePara = para.replace(/💫\s*/g, '• ');
          const wrapped = pdf.splitTextToSize(pdfSafePara, innerW) as string[];
          for (const line of wrapped) {
            let drawX = innerX;
            if (ts.textAlign === 'center') drawX = innerX + innerW / 2;
            else if (ts.textAlign === 'right') drawX = innerX + innerW;

            pdf.text(line, drawX, cursorY, {
              align: ts.textAlign === 'justify' ? 'left' : ts.textAlign,
              angle: el.rotation ? -el.rotation : 0,
            });
            cursorY += lineAdvanceMm;
          }
          cursorY += ts.paragraphSpacing * PT_TO_MM;
        }
      }

      if (el.opacity < 1 && pdfAny.GState && pdfAny.setGState) {
        pdfAny.setGState(new pdfAny.GState({ opacity: 1 }));
      }
    }
  }

  const slug = project.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'letterhead';
  const pdfBlob = pdf.output('blob');
  return saveOrShareBlobOnDevice(pdfBlob, `${slug}.pdf`, 'Share or Save Quotation PDF');
}

function mmToTwips(mm: number): number {
  return Math.round((mm / 25.4) * 1440);
}

function mmToEmuPx(mm: number): number {
  return Math.round((mm / 25.4) * 96);
}

export async function exportToDocx(
  project: LetterheadProject
): Promise<'native-shared' | 'web-downloaded'> {
  const { margins } = project.pageSettings;
  const pageZero = project.elements.filter((el) => el.pageIndex === 0 && el.visible);

  const logoEl = pageZero.find((e) => e.role === 'logo');
  const titleEl = pageZero.find((e) => e.role === 'company-title');
  const subtitleEl = pageZero.find((e) => e.role === 'company-subtitle');
  const contactEl = pageZero.find((e) => e.role === 'contact');
  const metaEl = pageZero.find((e) => e.role === 'event-meta');
  const bodyEl = pageZero.find((e) => e.role === 'body');
  const closingEl = pageZero.find((e) => e.role === 'closing');
  const bankEl = pageZero.find((e) => e.role === 'payment-bank');
  const mpesaEl = pageZero.find((e) => e.role === 'payment-mpesa');
  const waveEl = pageZero.find((e) => e.role === 'footer-wave');

  const children: Array<Paragraph | Table> = [];

  // 1. Header Table (Logo left, Brand & Contact right)
  const logoImg = logoEl?.imageSrc ? await loadImageAsUint8Array(logoEl.imageSrc) : null;
  const logoCellChildren: Paragraph[] = [];
  if (logoImg && logoEl) {
    logoCellChildren.push(
      new Paragraph({
        children: [
          new ImageRun({
            data: logoImg.bytes,
            transformation: {
              width: mmToEmuPx(logoEl.width),
              height: mmToEmuPx(logoEl.height),
            },
            type: 'png',
          }),
        ],
      })
    );
  } else {
    logoCellChildren.push(new Paragraph({ text: '' }));
  }

  const rightHeaderParagraphs: Paragraph[] = [];
  if (titleEl) {
    rightHeaderParagraphs.push(
      new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [
          new TextRun({
            text: titleEl.content || 'Grashy Caterers',
            bold: true,
            size: Math.round((titleEl.textStyle?.fontSize || 22) * 2),
            color: (titleEl.textStyle?.color || '#C45911').replace('#', ''),
            font: titleEl.textStyle?.fontFamily || 'DM Sans',
          }),
        ],
      })
    );
  }
  if (subtitleEl) {
    rightHeaderParagraphs.push(
      new Paragraph({
        alignment: AlignmentType.RIGHT,
        spacing: { after: 60 },
        children: [
          new TextRun({
            text: subtitleEl.content || '',
            bold: true,
            size: Math.round((subtitleEl.textStyle?.fontSize || 8) * 2),
            color: (subtitleEl.textStyle?.color || '#64748B').replace('#', ''),
            font: subtitleEl.textStyle?.fontFamily || 'DM Sans',
          }),
        ],
      })
    );
  }
  if (contactEl) {
    for (const line of (contactEl.content || '').split('\n')) {
      rightHeaderParagraphs.push(
        new Paragraph({
          alignment: AlignmentType.RIGHT,
          children: [
            new TextRun({
              text: line,
              size: Math.round((contactEl.textStyle?.fontSize || 9.5) * 2),
              color: (contactEl.textStyle?.color || '#334155').replace('#', ''),
              font: contactEl.textStyle?.fontFamily || 'DM Sans',
            }),
          ],
        })
      );
    }
  }

  children.push(
    new Table({
      width: { size: 100, type: WidthType.PERCENTAGE },
      borders: {
        top: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
        left: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
        right: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
        insideHorizontal: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
        insideVertical: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
        bottom: { style: BorderStyle.SINGLE, size: 12, color: 'C45911' },
      },
      rows: [
        new TableRow({
          children: [
            new TableCell({
              width: { size: 40, type: WidthType.PERCENTAGE },
              borders: {
                top: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
                left: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
                right: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
                bottom: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
              },
              children: logoCellChildren,
            }),
            new TableCell({
              width: { size: 60, type: WidthType.PERCENTAGE },
              borders: {
                top: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
                left: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
                right: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
                bottom: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
              },
              children: rightHeaderParagraphs.length > 0 ? rightHeaderParagraphs : [new Paragraph({ text: '' })],
            }),
          ],
        }),
      ],
    })
  );

  children.push(new Paragraph({ text: '', spacing: { before: 120, after: 120 } }));

  // 2. Event Metadata Block (supports both unified event-meta and split fields)
  const dateEl = pageZero.find((e) => e.role === 'event-date');
  const paxEl = pageZero.find((e) => e.role === 'event-pax');
  const clientEl = pageZero.find((e) => e.role === 'client-name');
  const venueEl = pageZero.find((e) => e.role === 'event-venue');
  const splitMetaTexts = [
    [dateEl?.content, paxEl?.content].filter(Boolean).join('   |   '),
    clientEl?.content,
    venueEl?.content,
  ].filter((t): t is string => Boolean(t && t.trim()));

  if (metaEl || splitMetaTexts.length > 0) {
    const rawLines = metaEl ? (metaEl.content || '').split('\n') : splitMetaTexts;
    const refStyle = metaEl?.textStyle || clientEl?.textStyle;
    const metaLines = rawLines.map(
      (line) =>
        new Paragraph({
          spacing: { after: 60 },
          children: [
            new TextRun({
              text: line,
              size: Math.round((refStyle?.fontSize || 10) * 2),
              color: (refStyle?.color || '#9A3412').replace('#', ''),
              font: refStyle?.fontFamily || 'DM Sans',
              bold: true,
            }),
          ],
        })
    );

    children.push(
      new Table({
        width: { size: 100, type: WidthType.PERCENTAGE },
        rows: [
          new TableRow({
            children: [
              new TableCell({
                shading: { type: ShadingType.CLEAR, fill: 'FFF7ED', color: 'FFF7ED' },
                margins: { top: 120, bottom: 120, left: 160, right: 160 },
                borders: {
                  top: { style: BorderStyle.SINGLE, size: 4, color: 'FDBA74' },
                  bottom: { style: BorderStyle.SINGLE, size: 4, color: 'FDBA74' },
                  left: { style: BorderStyle.SINGLE, size: 4, color: 'FDBA74' },
                  right: { style: BorderStyle.SINGLE, size: 4, color: 'FDBA74' },
                },
                children: metaLines,
              }),
            ],
          }),
        ],
      })
    );
    children.push(new Paragraph({ text: '', spacing: { after: 160 } }));
  }

  // 3. Body Content & Custom Text Elements
  const bodyAndCustom = pageZero
    .filter(
      (e) =>
        e.type === 'text' &&
        (e.role === 'body' || e.role === 'custom')
    )
    .sort((a, b) => a.y - b.y);

  for (const textEl of bodyAndCustom) {
    const ts = textEl.textStyle;
    const lines = (textEl.content || '').split('\n');
    for (const line of lines) {
      children.push(
        new Paragraph({
          spacing: { after: 80 },
          children: [
            new TextRun({
              text: line,
              size: Math.round((ts?.fontSize || 11) * 2),
              bold: (ts?.fontWeight || 400) >= 600,
              italics: ts?.fontStyle === 'italic',
              color: (ts?.color || '#1E293B').replace('#', ''),
              font: ts?.fontFamily || 'DM Sans',
            }),
          ],
        })
      );
    }
  }

  // 3b. Cost Summary Box right below Menu
  const costEl = pageZero.find((e) => e.role === 'cost-summary');
  if (costEl) {
    const costLines = (costEl.content || '').split('\n').map(
      (line, idx) =>
        new Paragraph({
          spacing: { after: 50 },
          children: [
            new TextRun({
              text: line,
              size: Math.round((costEl.textStyle?.fontSize || 10) * 2),
              bold: idx === 0 || idx === (costEl.content || '').split('\n').length - 1,
              color: (costEl.textStyle?.color || '#0F172A').replace('#', ''),
              font: costEl.textStyle?.fontFamily || 'Plus Jakarta Sans',
            }),
          ],
        })
    );

    children.push(new Paragraph({ text: '', spacing: { before: 80 } }));
    children.push(
      new Table({
        width: { size: 100, type: WidthType.PERCENTAGE },
        rows: [
          new TableRow({
            children: [
              new TableCell({
                shading: { type: ShadingType.CLEAR, fill: 'F8FAFC', color: 'F8FAFC' },
                margins: { top: 100, bottom: 100, left: 160, right: 160 },
                borders: {
                  top: { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1' },
                  bottom: { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1' },
                  left: { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1' },
                  right: { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1' },
                },
                children: costLines,
              }),
            ],
          }),
        ],
      })
    );
  }

  // 4. Closing Note
  if (closingEl) {
    children.push(
      new Paragraph({
        spacing: { before: 240, after: 120 },
        children: [
          new TextRun({
            text: closingEl.content || '',
            italics: true,
            bold: true,
            size: Math.round((closingEl.textStyle?.fontSize || 10.5) * 2),
            color: (closingEl.textStyle?.color || '#1E293B').replace('#', ''),
            font: closingEl.textStyle?.fontFamily || 'DM Sans',
          }),
        ],
      })
    );
  }

  // 5. Structured 2-column Payment Block
  if (bankEl || mpesaEl) {
    const bankLines = (bankEl?.content || '').split('\n').map(
      (line) =>
        new Paragraph({
          spacing: { after: 40 },
          children: [
            new TextRun({
              text: line,
              size: Math.round((bankEl?.textStyle?.fontSize || 9.5) * 2),
              color: (bankEl?.textStyle?.color || '#1E293B').replace('#', ''),
              font: bankEl?.textStyle?.fontFamily || 'DM Sans',
            }),
          ],
        })
    );
    const mpesaLines = (mpesaEl?.content || '').split('\n').map(
      (line) =>
        new Paragraph({
          spacing: { after: 40 },
          children: [
            new TextRun({
              text: line,
              size: Math.round((mpesaEl?.textStyle?.fontSize || 9.5) * 2),
              color: (mpesaEl?.textStyle?.color || '#1E293B').replace('#', ''),
              font: mpesaEl?.textStyle?.fontFamily || 'DM Sans',
            }),
          ],
        })
    );

    children.push(
      new Table({
        width: { size: 100, type: WidthType.PERCENTAGE },
        rows: [
          new TableRow({
            children: [
              new TableCell({
                width: { size: 55, type: WidthType.PERCENTAGE },
                shading: { type: ShadingType.CLEAR, fill: 'F8FAFC', color: 'F8FAFC' },
                margins: { top: 100, bottom: 100, left: 140, right: 140 },
                borders: {
                  top: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' },
                  bottom: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' },
                  left: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' },
                  right: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' },
                },
                children: bankLines.length > 0 ? bankLines : [new Paragraph({ text: '' })],
              }),
              new TableCell({
                width: { size: 45, type: WidthType.PERCENTAGE },
                shading: { type: ShadingType.CLEAR, fill: 'F8FAFC', color: 'F8FAFC' },
                margins: { top: 100, bottom: 100, left: 140, right: 140 },
                borders: {
                  top: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' },
                  bottom: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' },
                  left: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' },
                  right: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' },
                },
                children: mpesaLines.length > 0 ? mpesaLines : [new Paragraph({ text: '' })],
              }),
            ],
          }),
        ],
      })
    );
  }

  // 6. Footer Brand Wave
  if (waveEl?.imageSrc) {
    const waveImg = await loadImageAsUint8Array(waveEl.imageSrc);
    if (waveImg) {
      children.push(
        new Paragraph({
          spacing: { before: 200 },
          alignment: AlignmentType.CENTER,
          children: [
            new ImageRun({
              data: waveImg.bytes,
              transformation: {
                width: mmToEmuPx(180),
                height: mmToEmuPx(18),
              },
              type: 'png',
            }),
          ],
        })
      );
    }
  }

  // 7. Additional Continuation Pages (pageIndex >= 1)
  for (let pageIdx = 1; pageIdx < project.pageCount; pageIdx++) {
    const pageEls = project.elements
      .filter((el) => el.pageIndex === pageIdx && el.visible)
      .sort((a, b) => a.y - b.y);

    children.push(
      new Paragraph({
        pageBreakBefore: true,
        text: '',
      })
    );

    for (const el of pageEls) {
      if (el.type === 'image' && el.imageSrc) {
        const img = await loadImageAsUint8Array(el.imageSrc);
        if (img) {
          children.push(
            new Paragraph({
              spacing: { before: 80, after: 80 },
              children: [
                new ImageRun({
                  data: img.bytes,
                  transformation: {
                    width: mmToEmuPx(el.width),
                    height: mmToEmuPx(el.height),
                  },
                  type: 'png',
                }),
              ],
            })
          );
        }
      } else if (el.type === 'text' && el.textStyle) {
        const ts = el.textStyle;
        for (const line of (el.content || '').split('\n')) {
          children.push(
            new Paragraph({
              spacing: { after: 80 },
              children: [
                new TextRun({
                  text: line,
                  size: Math.round((ts.fontSize || 10.5) * 2),
                  bold: (ts.fontWeight || 400) >= 600,
                  italics: ts.fontStyle === 'italic',
                  color: normalizeDocxHex(ts.color, '1E293B'),
                  font: ts.fontFamily || 'DM Sans',
                }),
              ],
            })
          );
        }
      }
    }
  }

  void bodyEl;

  const doc = new Document({
    sections: [
      {
        properties: {
          page: {
            size: {
              width: mmToTwips(210),
              height: mmToTwips(297),
            },
            margin: {
              top: mmToTwips(margins.top),
              right: mmToTwips(margins.right),
              bottom: mmToTwips(margins.bottom),
              left: mmToTwips(margins.left),
            },
          },
        },
        children,
      },
    ],
  });

  const blob = await Packer.toBlob(doc);
  const slug = project.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'letterhead';
  return saveOrShareBlobOnDevice(blob, `${slug}.docx`, 'Share or Save Word Document');
}

export async function exportProjectJson(
  project: LetterheadProject
): Promise<'native-shared' | 'web-downloaded'> {
  const json = JSON.stringify(project, null, 2);
  const blob = new Blob([json], { type: 'application/json' });
  const slug = project.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'letterhead';
  return saveOrShareBlobOnDevice(blob, `${slug}.grashy.json`, 'Share or Save Project Backup');
}

export interface ImportResult {
  kind: 'project' | 'elements';
  project?: LetterheadProject;
  addedElements?: LetterheadElement[];
  message: string;
}

export async function importFileIntoProject(
  file: File,
  activePageIndex: number,
  nextZIndex: number
): Promise<ImportResult> {
  const ext = file.name.split('.').pop()?.toLowerCase() || '';

  // 1. Saved Project JSON
  if (ext === 'json') {
    const text = await file.text();
    const parsed = JSON.parse(text) as LetterheadProject;
    if (!parsed.elements || !parsed.pageSettings) {
      throw new Error('Invalid project file format.');
    }
    return {
      kind: 'project',
      project: parsed,
      message: `Loaded saved project "${parsed.name}"`,
    };
  }

  // 2. Image Import (PNG, JPG, WEBP, SVG)
  if (['png', 'jpg', 'jpeg', 'webp', 'svg'].includes(ext) || file.type.startsWith('image/')) {
    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(new Error('Failed to read image file.'));
      reader.readAsDataURL(file);
    });

    const dims = await loadImageAsDataUrl(dataUrl);
    const ratio = dims && dims.height > 0 ? dims.width / dims.height : 1.5;
    const widthMm = Math.min(90, Math.max(30, 55));
    const heightMm = roundMm(widthMm / ratio);

    const imgEl: LetterheadElement = {
      id: `imported-img-${Date.now()}`,
      pageIndex: activePageIndex,
      type: 'image',
      role: 'custom',
      name: file.name,
      x: 20,
      y: 20,
      width: widthMm,
      height: heightMm,
      rotation: 0,
      opacity: 1,
      zIndex: nextZIndex,
      locked: false,
      visible: true,
      imageSrc: dataUrl,
      objectFit: 'contain',
    };

    return {
      kind: 'elements',
      addedElements: [imgEl],
      message: `Imported image "${file.name}"`,
    };
  }

  // 3. DOCX Import (XML paragraphs + media images)
  if (ext === 'docx') {
    const buf = await file.arrayBuffer();
    const zip = await JSZip.loadAsync(buf);
    const docXmlFile = zip.file('word/document.xml');
    if (!docXmlFile) throw new Error('Invalid DOCX: missing word/document.xml');

    const xmlText = await docXmlFile.async('text');
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(xmlText, 'application/xml');
    const paragraphs = Array.from(xmlDoc.getElementsByTagName('w:p'));
    const extractedLines: string[] = [];

    for (const p of paragraphs) {
      const runs = Array.from(p.getElementsByTagName('w:t'));
      const line = runs.map((t) => t.textContent || '').join('').trim();
      if (line) extractedLines.push(line);
    }

    const added: LetterheadElement[] = [];
    if (extractedLines.length > 0) {
      added.push({
        id: `docx-text-${Date.now()}`,
        pageIndex: activePageIndex,
        type: 'text',
        role: 'custom',
        name: `DOCX Text (${file.name})`,
        x: 15,
        y: 80,
        width: 180,
        height: Math.min(130, Math.max(24, extractedLines.length * 7)),
        rotation: 0,
        opacity: 1,
        zIndex: nextZIndex,
        locked: false,
        visible: true,
        content: extractedLines.join('\n'),
        textStyle: {
          fontFamily: 'DM Sans',
          fontSize: 10.5,
          fontWeight: 400,
          fontStyle: 'normal',
          letterSpacing: 0,
          lineHeight: 1.45,
          paragraphSpacing: 2,
          color: '#1E293B',
          textAlign: 'left',
          paddingTop: 2,
          paddingRight: 2,
          paddingBottom: 2,
          paddingLeft: 2,
        },
      });
    }

    // Extract first embedded image if present
    const mediaFiles = Object.keys(zip.files).filter((k) => k.startsWith('word/media/'));
    if (mediaFiles.length > 0) {
      const firstMedia = zip.file(mediaFiles[0]);
      if (firstMedia) {
        const b64 = await firstMedia.async('base64');
        const mime = mediaFiles[0].endsWith('.png') ? 'image/png' : 'image/jpeg';
        added.push({
          id: `docx-img-${Date.now()}`,
          pageIndex: activePageIndex,
          type: 'image',
          role: 'custom',
          name: `DOCX Asset (${mediaFiles[0].split('/').pop()})`,
          x: 15,
          y: 14,
          width: 45,
          height: 30,
          rotation: 0,
          opacity: 1,
          zIndex: nextZIndex + 5,
          locked: false,
          visible: true,
          imageSrc: `data:${mime};base64,${b64}`,
          objectFit: 'contain',
        });
      }
    }

    if (added.length === 0) {
      throw new Error('No readable text or images found in DOCX.');
    }

    return {
      kind: 'elements',
      addedElements: added,
      message: `Imported ${added.length} element(s) from "${file.name}"`,
    };
  }

  // 4. PDF Import (extracts text blocks into editable text elements)
  if (ext === 'pdf') {
    const buf = await file.arrayBuffer();
    const loadingTask = pdfjsLib.getDocument({ data: new Uint8Array(buf) });
    const pdfDoc = await loadingTask.promise;
    const page = await pdfDoc.getPage(1);
    const viewport = page.getViewport({ scale: 1 });
    const textContent = await page.getTextContent();

    const pxToMmX = 210 / viewport.width;
    const pxToMmY = 297 / viewport.height;

    // Group text items by approximate Y row
    const rows = new Map<number, string[]>();
    for (const item of textContent.items) {
      if ('str' in item && item.str.trim()) {
        const yPt = viewport.height - item.transform[5];
        const bucket = Math.round((yPt * pxToMmY) / 4) * 4;
        if (!rows.has(bucket)) rows.set(bucket, []);
        rows.get(bucket)!.push(item.str);
      }
    }

    const sortedLines = Array.from(rows.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([, parts]) => parts.join(' ').trim())
      .filter(Boolean);

    const added: LetterheadElement[] = [];
    if (sortedLines.length > 0) {
      added.push({
        id: `pdf-text-${Date.now()}`,
        pageIndex: activePageIndex,
        type: 'text',
        role: 'custom',
        name: `PDF Extract (${file.name})`,
        x: roundMm(15 * (pxToMmX / pxToMmX)),
        y: 80,
        width: 180,
        height: Math.min(140, Math.max(24, sortedLines.length * 6.5)),
        rotation: 0,
        opacity: 1,
        zIndex: nextZIndex,
        locked: false,
        visible: true,
        content: sortedLines.join('\n'),
        textStyle: {
          fontFamily: 'DM Sans',
          fontSize: 10.5,
          fontWeight: 400,
          fontStyle: 'normal',
          letterSpacing: 0,
          lineHeight: 1.45,
          paragraphSpacing: 2,
          color: '#1E293B',
          textAlign: 'left',
          paddingTop: 2,
          paddingRight: 2,
          paddingBottom: 2,
          paddingLeft: 2,
        },
      });
    } else {
      // Fallback: render page 1 to canvas image if scanned PDF
      const scale = 2;
      const vp2 = page.getViewport({ scale });
      const canvas = document.createElement('canvas');
      canvas.width = vp2.width;
      canvas.height = vp2.height;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        await page.render({ canvasContext: ctx, viewport: vp2, canvas }).promise;
        added.push({
          id: `pdf-page-${Date.now()}`,
          pageIndex: activePageIndex,
          type: 'image',
          role: 'custom',
          name: `PDF Page (${file.name})`,
          x: 0,
          y: 0,
          width: 210,
          height: 297,
          rotation: 0,
          opacity: 1,
          zIndex: 1,
          locked: false,
          visible: true,
          imageSrc: canvas.toDataURL('image/png'),
          objectFit: 'contain',
        });
      }
    }

    return {
      kind: 'elements',
      addedElements: added,
      message: `Imported PDF "${file.name}"`,
    };
  }

  throw new Error(`Unsupported file type ".${ext}". Use PDF, DOCX, PNG/JPG/SVG, or .grashy.json.`);
}
