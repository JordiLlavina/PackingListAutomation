import { describe, expect, it } from 'vitest';
import { estadoPush, urlBase64ToUint8Array } from './push';

describe('urlBase64ToUint8Array', () => {
  it('pasa la clave en Base64 URL, sin relleno, a sus bytes', () => {
    // "+/8=" en Base64 normal: 0xfb 0xff; en Base64 URL sin relleno, "-_8".
    expect(Array.from(urlBase64ToUint8Array('-_8'))).toEqual([0xfb, 0xff]);
    expect(Array.from(urlBase64ToUint8Array('AQID'))).toEqual([1, 2, 3]);
    expect(Array.from(urlBase64ToUint8Array('AQ'))).toEqual([1]);
  });

  it('una clave pública VAPID son los 65 bytes de un punto sin comprimir (empieza por 4)', () => {
    const clave = 'BAubwzk1hsN8Gv2PgyWeD13iQ4mKdFA-JdA6Jq6SXJAzXDzWSb7yb1yC3EqL1l0dpN_fBgRJbDqYzj3VDDXcbNY';
    const bytes = urlBase64ToUint8Array(clave);
    expect(bytes.length).toBe(65);
    expect(bytes[0]).toBe(4);
  });
});

describe('estadoPush', () => {
  it('sin navegador (en los tests no hay window) no hay push', () => {
    expect(estadoPush()).toBe('no-soportado');
  });
});
