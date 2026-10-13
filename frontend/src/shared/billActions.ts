import {openPdf, salePdf, sendInvoiceWhatsApp} from '../api';
import {errText} from './kit';

/** Opens the bill PDF in the company's chosen invoice design and paper size. */
export async function printBill(id: number, notify: (s: string) => void) {
  try { const r = await salePdf(id); openPdf(r.data, `invoice-${id}.pdf`); }
  catch (e) { notify(errText(e, 'The invoice could not be opened.')); }
}

/** Sends the invoice through WhatsApp Business when it is set up; otherwise (or if sending fails) opens WhatsApp on this
 *  device with the message ready, and says so - it never reports a message as sent when it was not. */
export async function shareBillOnWhatsApp(id: number, notify: (s: string) => void) {
  try {
    const r = await sendInvoiceWhatsApp(id);
    const d = r.data || {};
    if (d.status !== 'SENT' && d.shareUrl) window.open(d.shareUrl, '_blank', 'noopener');
    notify(d.message || (d.status === 'SENT' ? 'Invoice sent on WhatsApp.' : 'Opening WhatsApp on this device.'));
  } catch (e) { notify(errText(e)); }
}
