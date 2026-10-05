/**
 * Global dynamic branding.
 * The company name entered at registration (and shown in Settings) is the brand everywhere in the app:
 * sidebar, header, loading screen, login portal, browser tab and PDF receipts (server side).
 * The last known company name is remembered in this browser so the login screen of a returning user already shows it.
 */
const KEY = 'j360_brand_name';
export const DEFAULT_BRAND = 'Jewellery360';

export const getBrandName = (): string => {
  try {
    return localStorage.getItem(KEY) || DEFAULT_BRAND;
  } catch {
    return DEFAULT_BRAND;
  }
};

export const rememberBrandName = (name?: string | null): string => {
  const clean = String(name || '').trim();
  if (!clean) return getBrandName();
  try {
    localStorage.setItem(KEY, clean);
  } catch {
    /* storage unavailable (private mode) - the name simply is not remembered */
  }
  return clean;
};

/** Up to three letters for the round logo mark: "Sri Lakshmi Jewellers" -> "SLJ", "Jewellery360" -> "JE". */
export const brandInitials = (name: string): string => {
  const words = String(name || '').trim().split(/\s+/).filter(Boolean);
  if (words.length >= 2) return words.slice(0, 3).map(w => w[0]).join('').toUpperCase();
  return (words[0] || 'J').slice(0, 2).toUpperCase();
};
