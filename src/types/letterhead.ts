export type ElementType = 'text' | 'image' | 'line' | 'shape';

export type ElementRole =
  | 'logo'
  | 'company-title'
  | 'company-subtitle'
  | 'contact'
  | 'separator'
  | 'event-meta'
  | 'event-date'
  | 'event-pax'
  | 'client-name'
  | 'event-venue'
  | 'body'
  | 'cost-summary'
  | 'closing'
  | 'payment-bank'
  | 'payment-mpesa'
  | 'payment-divider'
  | 'footer-wave'
  | 'custom';

export type EditorMode = 'form' | 'visual';

export interface LetterheadFormData {
  // A. Document Details
  date: string;
  clientName: string;
  pax: string;
  venue: string;
  eventDetails: string;

  // B. Payment Information
  transportCost: string;
  subtotalCost: string;
  vatAmount: string;
  totalAmount: string;
  depositAmount: string;
  balance: string;
  paymentMethod: string;
  bankDetails: string;
  mpesaDetails: string;
  paymentInstructions: string;

  // C. Sender Details
  companyName: string;
  contactPerson: string;
  phoneNumber: string;
  email: string;
  physicalAddress: string;
  postalAddress: string;
}

export type TextAlign = 'left' | 'center' | 'right' | 'justify';

export interface TextStyle {
  fontFamily: string;
  fontSize: number; // points (pt)
  fontWeight: number; // 400 | 500 | 600 | 700 | 800
  fontStyle: 'normal' | 'italic';
  letterSpacing: number; // em
  lineHeight: number; // unitless multiplier
  paragraphSpacing: number; // pt
  color: string;
  textAlign: TextAlign;
  paddingTop: number; // mm
  paddingRight: number; // mm
  paddingBottom: number; // mm
  paddingLeft: number; // mm
}

export interface ShapeStyle {
  fill: string;
  stroke: string;
  strokeWidth: number; // pt
  borderRadius: number; // mm
  lineStyle: 'solid' | 'dashed' | 'dotted';
}

export interface LetterheadElement {
  id: string;
  pageIndex: number; // 0-based
  type: ElementType;
  role: ElementRole;
  name: string;
  x: number; // mm from left of A4 page
  y: number; // mm from top of A4 page
  width: number; // mm
  height: number; // mm
  rotation: number; // degrees
  opacity: number; // 0..1
  zIndex: number;
  locked: boolean;
  visible: boolean;
  content?: string;
  textStyle?: TextStyle;
  shapeStyle?: ShapeStyle;
  imageSrc?: string;
  objectFit?: 'contain' | 'cover' | 'fill';
}

export interface PageMargins {
  top: number; // mm
  right: number; // mm
  bottom: number; // mm
  left: number; // mm
}

export interface PageSettings {
  widthMm: number; // 210
  heightMm: number; // 297
  margins: PageMargins;
  showMargins: boolean;
  showGrid: boolean;
  showRulers: boolean;
  snapToGrid: boolean;
  snapToGuides: boolean;
  gridSizeMm: number;
}

export interface LetterheadProject {
  id: string;
  name: string;
  version: number;
  updatedAt: string;
  pageCount: number;
  layoutMode: 'original' | 'refined' | 'custom';
  formData: LetterheadFormData;
  pageSettings: PageSettings;
  elements: LetterheadElement[];
}

export type AuditCategory =
  | 'Logo & Header'
  | 'Typography'
  | 'Spacing & Balance'
  | 'Contact & Footer'
  | 'Margins & Alignment';

export type AuditSeverity = 'high' | 'medium' | 'low';

export interface DesignSuggestion {
  id: string;
  category: AuditCategory;
  severity: AuditSeverity;
  title: string;
  explanation: string;
  affectedElementIds: string[];
  fixLabel: string;
  canAutoFix: boolean;
  applyFix?: (project: LetterheadProject) => LetterheadProject;
}

export interface SnapGuide {
  axis: 'x' | 'y';
  positionMm: number;
  kind: 'margin' | 'page-center' | 'element';
}

export interface ParsedQuotation {
  date: string;
  pax: string;
  client: string;
  venue: string;
  menuItems: string[];
  ratePerPlate: string;
  transport: string;
  subtotal: string;
  vat: string;
  totalCost: string;
  extraNotes: string[];
}

export interface FontPairing {
  id: string;
  name: string;
  description: string;
  displayFont: string;
  bodyFont: string;
}
