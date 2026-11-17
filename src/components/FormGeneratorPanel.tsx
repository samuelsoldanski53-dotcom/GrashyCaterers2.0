import React, { useState } from 'react';
import {
  FileCheck2,
  UserPlus,
  PenTool,
  ClipboardPaste,
  ChevronDown,
  ChevronRight,
  Eye,
} from 'lucide-react';
import { LetterheadFormData } from '../types/letterhead';
import { SUNCULTURE_SAMPLE_QUOTE } from '../utils/quoteParser';

interface FormGeneratorPanelProps {
  formData: LetterheadFormData;
  onChangeForm: (patch: Partial<LetterheadFormData>) => void;
  onApplyRawQuote: (rawText: string) => void;
  onGenerateDocument: () => void;
  onNewClient: () => void;
  onSwitchToVisual: () => void;
  onMobilePreview?: () => void;
}

export const FormGeneratorPanel: React.FC<FormGeneratorPanelProps> = ({
  formData,
  onChangeForm,
  onApplyRawQuote,
  onGenerateDocument,
  onNewClient,
  onSwitchToVisual,
  onMobilePreview,
}) => {
  const [rawPaste, setRawPaste] = useState('');
  const [quickPasteOpen, setQuickPasteOpen] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onGenerateDocument();
  };

  return (
    <aside
      aria-label="Document Generator Form"
      className="w-full lg:w-96 bg-white border-r border-slate-200 flex flex-col h-full shrink-0"
    >
      {/* Header */}
      <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between gap-2 shrink-0 bg-slate-50/70">
        <div>
          <h1 className="text-xs font-bold uppercase tracking-wider text-slate-900">
            Document Generator
          </h1>
          <p className="text-[11px] text-slate-500 mt-0.5">
            Fill client &amp; event fields — preview updates live
          </p>
        </div>
        <button
          type="button"
          onClick={onNewClient}
          className="flex items-center gap-1 px-2.5 py-1.5 rounded-md border border-slate-200 bg-white hover:bg-slate-50 text-xs font-medium text-slate-700 whitespace-nowrap shrink-0 transition-colors"
        >
          <UserPlus className="w-3.5 h-3.5 text-[#C45911]" />
          New Client
        </button>
      </div>

      {/* Scrollable Form */}
      <form
        id="document-generator-form"
        onSubmit={handleSubmit}
        className="flex-1 overflow-y-auto px-4 py-3.5 space-y-5"
      >
        {/* Optional Quick Quote Auto-Fill */}
        <div className="rounded-lg border border-slate-200 bg-slate-50/70 overflow-hidden">
          <button
            type="button"
            onClick={() => setQuickPasteOpen((v) => !v)}
            aria-expanded={quickPasteOpen}
            className="w-full px-3 py-2 flex items-center justify-between text-xs font-semibold text-slate-800 hover:bg-slate-100/70 transition-colors"
          >
            <span className="flex items-center gap-1.5">
              <ClipboardPaste className="w-3.5 h-3.5 text-[#C45911]" />
              Quick Quote Auto-Fill (WhatsApp / Notes)
            </span>
            {quickPasteOpen ? (
              <ChevronDown className="w-3.5 h-3.5 text-slate-500" />
            ) : (
              <ChevronRight className="w-3.5 h-3.5 text-slate-500" />
            )}
          </button>

          {quickPasteOpen && (
            <div className="px-3 pb-3 pt-1 space-y-2 border-t border-slate-200 bg-white">
              <div className="flex items-center justify-between">
                <label htmlFor="form-quick-paste" className="text-[11px] font-medium text-slate-600">
                  Paste catering note to populate fields
                </label>
                <button
                  type="button"
                  onClick={() => {
                    setRawPaste(SUNCULTURE_SAMPLE_QUOTE);
                    onApplyRawQuote(SUNCULTURE_SAMPLE_QUOTE);
                  }}
                  className="text-[11px] font-semibold text-[#C45911] hover:underline"
                >
                  Load Sample
                </button>
              </div>
              <textarea
                id="form-quick-paste"
                rows={3}
                value={rawPaste}
                onChange={(e) => {
                  const val = e.target.value;
                  setRawPaste(val);
                  if (val.trim().length > 5) {
                    onApplyRawQuote(val);
                  }
                }}
                placeholder="Date: 13/11/2026 Pax: 200 Client: Sunculture Kenya Ltd Venue: Muthangari Drive 💫..."
                className="w-full text-xs p-2 rounded-md border border-slate-200 focus:border-[#C45911] outline-none leading-relaxed"
              />
            </div>
          )}
        </div>

        {/* SECTION A: DOCUMENT DETAILS */}
        <section aria-labelledby="section-doc-details" className="space-y-3">
          <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
            <h2
              id="section-doc-details"
              className="text-xs font-bold uppercase tracking-wider text-slate-800"
            >
              A. Document Details
            </h2>
          </div>

          <div className="grid grid-cols-2 gap-2.5">
            <div>
              <label htmlFor="field-date" className="block text-[11px] font-medium text-slate-600 mb-1">
                Date
              </label>
              <input
                id="field-date"
                type="text"
                value={formData.date}
                onChange={(e) => onChangeForm({ date: e.target.value })}
                placeholder="e.g. 13/11/2026"
                className="w-full text-xs font-mono tabular-nums px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>

            <div>
              <label htmlFor="field-pax" className="block text-[11px] font-medium text-slate-600 mb-1">
                PAX (No. of People)
              </label>
              <input
                id="field-pax"
                type="text"
                value={formData.pax}
                onChange={(e) => onChangeForm({ pax: e.target.value })}
                placeholder="e.g. 200"
                className="w-full text-xs font-mono tabular-nums px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>
          </div>

          <div>
            <label htmlFor="field-client-name" className="block text-[11px] font-medium text-slate-600 mb-1">
              Client Name
            </label>
            <input
              id="field-client-name"
              type="text"
              value={formData.clientName}
              onChange={(e) => onChangeForm({ clientName: e.target.value })}
              placeholder="e.g. Sunculture Kenya Ltd"
              className="w-full text-xs px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
            />
          </div>

          <div>
            <label htmlFor="field-venue" className="block text-[11px] font-medium text-slate-600 mb-1">
              Venue
            </label>
            <input
              id="field-venue"
              type="text"
              value={formData.venue}
              onChange={(e) => onChangeForm({ venue: e.target.value })}
              placeholder="e.g. Muthangari Drive, Nairobi"
              className="w-full text-xs px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="field-event-details" className="text-[11px] font-medium text-slate-600">
                Event Details / Menu
              </label>
              <button
                type="button"
                onClick={() => {
                  const cleaned = formData.eventDetails
                    .replace(/💫\s*/g, '\n•  ')
                    .replace(/\n{3,}/g, '\n\n')
                    .trim();
                  onChangeForm({ eventDetails: cleaned });
                }}
                className="text-[11px] font-medium text-[#C45911] hover:underline"
              >
                Clean Bullets
              </button>
            </div>
            <textarea
              id="field-event-details"
              rows={7}
              value={formData.eventDetails}
              onChange={(e) => onChangeForm({ eventDetails: e.target.value })}
              placeholder="Menu items, plate rate, service schedule..."
              className="w-full text-xs p-2.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white leading-relaxed"
            />
          </div>
        </section>

        {/* SECTION B: PAYMENT INFORMATION */}
        <section aria-labelledby="section-payment-info" className="space-y-3 pt-1">
          <div className="border-b border-slate-200 pb-1.5">
            <h2
              id="section-payment-info"
              className="text-xs font-bold uppercase tracking-wider text-slate-800"
            >
              B. Payment Information
            </h2>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            <div>
              <label htmlFor="field-transport-cost" className="block text-[11px] font-medium text-slate-600 mb-1">
                Transport
              </label>
              <input
                id="field-transport-cost"
                type="text"
                value={formData.transportCost}
                onChange={(e) => onChangeForm({ transportCost: e.target.value })}
                placeholder="5,000/="
                className="w-full text-xs font-mono tabular-nums px-2 py-2 sm:py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>
            <div>
              <label htmlFor="field-subtotal-cost" className="block text-[11px] font-medium text-slate-600 mb-1">
                Total Cost (Excl. VAT)
              </label>
              <input
                id="field-subtotal-cost"
                type="text"
                value={formData.subtotalCost}
                onChange={(e) => onChangeForm({ subtotalCost: e.target.value })}
                placeholder="175,000/="
                className="w-full text-xs font-mono tabular-nums px-2 py-2 sm:py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>
            <div className="col-span-2 sm:col-span-1">
              <label htmlFor="field-vat-amount" className="block text-[11px] font-medium text-slate-600 mb-1">
                VAT
              </label>
              <input
                id="field-vat-amount"
                type="text"
                value={formData.vatAmount}
                onChange={(e) => onChangeForm({ vatAmount: e.target.value })}
                placeholder="28,000/="
                className="w-full text-xs font-mono tabular-nums px-2 py-2 sm:py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            <div className="col-span-2 sm:col-span-1">
              <label htmlFor="field-total-amount" className="block text-[11px] font-medium text-slate-600 mb-1">
                Total Amount
              </label>
              <input
                id="field-total-amount"
                type="text"
                value={formData.totalAmount}
                onChange={(e) => onChangeForm({ totalAmount: e.target.value })}
                placeholder="KES 203,000/="
                className="w-full text-xs font-mono tabular-nums px-2 py-2 sm:py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>
            <div>
              <label htmlFor="field-deposit-amount" className="block text-[11px] font-medium text-slate-600 mb-1">
                Deposit Amount
              </label>
              <input
                id="field-deposit-amount"
                type="text"
                value={formData.depositAmount}
                onChange={(e) => onChangeForm({ depositAmount: e.target.value })}
                placeholder="KES 101,500/="
                className="w-full text-xs font-mono tabular-nums px-2 py-2 sm:py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>
            <div>
              <label htmlFor="field-balance" className="block text-[11px] font-medium text-slate-600 mb-1">
                Balance
              </label>
              <input
                id="field-balance"
                type="text"
                value={formData.balance}
                onChange={(e) => onChangeForm({ balance: e.target.value })}
                placeholder="KES 101,500/="
                className="w-full text-xs font-mono tabular-nums px-2 py-2 sm:py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>
          </div>

          <div>
            <label htmlFor="field-payment-method" className="block text-[11px] font-medium text-slate-600 mb-1">
              Payment Method
            </label>
            <input
              id="field-payment-method"
              type="text"
              value={formData.paymentMethod}
              onChange={(e) => onChangeForm({ paymentMethod: e.target.value })}
              placeholder="Bank Transfer / M-Pesa Buy Goods"
              className="w-full text-xs px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
            />
          </div>

          <div>
            <label htmlFor="field-bank-details" className="block text-[11px] font-medium text-slate-600 mb-1">
              Bank Details
            </label>
            <textarea
              id="field-bank-details"
              rows={4}
              value={formData.bankDetails}
              onChange={(e) => onChangeForm({ bankDetails: e.target.value })}
              className="w-full text-xs p-2 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white leading-relaxed"
            />
          </div>

          <div>
            <label htmlFor="field-mpesa-details" className="block text-[11px] font-medium text-slate-600 mb-1">
              M-Pesa Details
            </label>
            <textarea
              id="field-mpesa-details"
              rows={2}
              value={formData.mpesaDetails}
              onChange={(e) => onChangeForm({ mpesaDetails: e.target.value })}
              className="w-full text-xs p-2 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white leading-relaxed"
            />
          </div>

          <div>
            <label htmlFor="field-payment-instructions" className="block text-[11px] font-medium text-slate-600 mb-1">
              Payment Instructions &amp; Closing Note
            </label>
            <textarea
              id="field-payment-instructions"
              rows={3}
              value={formData.paymentInstructions}
              onChange={(e) => onChangeForm({ paymentInstructions: e.target.value })}
              className="w-full text-xs p-2 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white leading-relaxed"
            />
          </div>
        </section>

        {/* SECTION C: SENDER DETAILS */}
        <section aria-labelledby="section-sender-details" className="space-y-3 pt-1 pb-2">
          <div className="border-b border-slate-200 pb-1.5">
            <h2
              id="section-sender-details"
              className="text-xs font-bold uppercase tracking-wider text-slate-800"
            >
              C. Sender Details
            </h2>
          </div>

          <div className="grid grid-cols-2 gap-2.5">
            <div>
              <label htmlFor="field-company-name" className="block text-[11px] font-medium text-slate-600 mb-1">
                Company Name
              </label>
              <input
                id="field-company-name"
                type="text"
                value={formData.companyName}
                onChange={(e) => onChangeForm({ companyName: e.target.value })}
                className="w-full text-xs px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>

            <div>
              <label htmlFor="field-contact-person" className="block text-[11px] font-medium text-slate-600 mb-1">
                Sender / Contact Person
              </label>
              <input
                id="field-contact-person"
                type="text"
                value={formData.contactPerson}
                onChange={(e) => onChangeForm({ contactPerson: e.target.value })}
                className="w-full text-xs px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>

            <div>
              <label htmlFor="field-phone" className="block text-[11px] font-medium text-slate-600 mb-1">
                Phone Number
              </label>
              <input
                id="field-phone"
                type="text"
                value={formData.phoneNumber}
                onChange={(e) => onChangeForm({ phoneNumber: e.target.value })}
                className="w-full text-xs font-mono tabular-nums px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>

            <div>
              <label htmlFor="field-email" className="block text-[11px] font-medium text-slate-600 mb-1">
                Email
              </label>
              <input
                id="field-email"
                type="email"
                value={formData.email}
                onChange={(e) => onChangeForm({ email: e.target.value })}
                className="w-full text-xs px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
              />
            </div>
          </div>

          <div>
            <label htmlFor="field-physical-address" className="block text-[11px] font-medium text-slate-600 mb-1">
              Physical Address
            </label>
            <input
              id="field-physical-address"
              type="text"
              value={formData.physicalAddress}
              onChange={(e) => onChangeForm({ physicalAddress: e.target.value })}
              className="w-full text-xs px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
            />
          </div>

          <div>
            <label htmlFor="field-postal-address" className="block text-[11px] font-medium text-slate-600 mb-1">
              Postal Address
            </label>
            <input
              id="field-postal-address"
              type="text"
              value={formData.postalAddress}
              onChange={(e) => onChangeForm({ postalAddress: e.target.value })}
              className="w-full text-xs px-2.5 py-1.5 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
            />
          </div>
        </section>
      </form>

      {/* Sticky Footer Actions */}
      <div className="p-3 border-t border-slate-200 bg-white flex items-center gap-2 shrink-0">
        <button
          type="submit"
          form="document-generator-form"
          className="flex-1 flex items-center justify-center gap-1.5 py-2.5 sm:py-2 px-3 rounded-md bg-[#C45911] hover:bg-[#A8490C] text-white text-xs font-semibold whitespace-nowrap transition-colors"
        >
          <FileCheck2 className="w-3.5 h-3.5" />
          Generate Document
        </button>
        {onMobilePreview && (
          <button
            type="button"
            onClick={onMobilePreview}
            className="lg:hidden flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-md border border-slate-300 bg-white hover:bg-slate-50 text-slate-800 text-xs font-semibold whitespace-nowrap shrink-0 transition-colors"
          >
            <Eye className="w-3.5 h-3.5 text-[#C45911]" />
            Preview
          </button>
        )}
        <button
          type="button"
          onClick={onSwitchToVisual}
          className="hidden sm:flex items-center justify-center gap-1.5 py-2 px-3 rounded-md border border-slate-300 bg-white hover:bg-slate-50 text-slate-800 text-xs font-semibold whitespace-nowrap shrink-0 transition-colors"
        >
          <PenTool className="w-3.5 h-3.5 text-[#C45911]" />
          Edit Visually
        </button>
      </div>
    </aside>
  );
};
