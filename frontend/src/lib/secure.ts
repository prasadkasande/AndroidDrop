import { api } from './api';

/** Where a phone downloads the certificate that makes it trust this server's HTTPS. */
export const CERTIFICATE_URL = '/androiddrop-ca.crt';

/**
 * The HTTPS address to move to, for a phone on plain HTTP. Null on the computer itself: a browser already treats
 * http://localhost as secure, so the host never needs the certificate.
 */
export function secureOrigin(httpsPort: number | null): string | null {
  const local = location.hostname === 'localhost' || location.hostname === '127.0.0.1' || location.hostname.endsWith('.localhost');
  if (!httpsPort || location.protocol !== 'http:' || local) return null;
  return httpsPort === 443 ? `https://${location.hostname}` : `https://${location.hostname}:${httpsPort}`;
}

/**
 * Whether this device trusts the server's certificate: an image only loads over HTTPS if it does. An image rather
 * than fetch, because it needs no CORS from the other origin.
 */
function trustsCertificate(origin: string, timeoutMs = 2500): Promise<boolean> {
  return new Promise((resolve) => {
    const image = new Image();
    const timer = window.setTimeout(() => resolve(false), timeoutMs);
    image.onload = () => {
      window.clearTimeout(timer);
      resolve(true);
    };
    image.onerror = () => {
      window.clearTimeout(timer);
      resolve(false);
    };
    image.src = `${origin}/favicon.ico?probe=${Date.now()}`;
  });
}

/** Reads a query parameter and removes it from the address bar, keeping the others. */
export function takeParam(name: string): string | null {
  const url = new URL(location.href);
  const value = url.searchParams.get(name);
  if (value !== null) {
    url.searchParams.delete(name);
    history.replaceState(null, '', url.pathname + url.search + url.hash);
  }
  return value;
}

/**
 * Moves this phone to the HTTPS address if it trusts the certificate, carrying its pairing along. Resolves false
 * (and stays) if it doesn't trust it yet.
 */
export async function moveToSecure(origin: string, paired: boolean): Promise<boolean> {
  if (!(await trustsCertificate(origin))) return false;
  if (!paired) {
    location.replace(origin + location.pathname + location.search);
    return true;
  }
  const handoff = await api.startHandoff().catch(() => null);
  if (!handoff) return false;
  location.replace(`${origin}/?handoff=${encodeURIComponent(handoff.code)}`);
  return true;
}
