import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Grabador, type Grabacion } from './grabador';

/**
 * Una MediaRecorder de mentira con lo que hace la de verdad al parar: pasa a
 * `inactive`, entrega el último trozo (`dataavailable`) y luego `stop`.
 */
class GrabadoraFalsa extends EventTarget {
  static ultima: GrabadoraFalsa | null = null;
  static isTypeSupported = () => true;
  state: 'inactive' | 'recording' = 'inactive';
  mimeType = 'audio/webm;codecs=opus';

  constructor() {
    super();
    GrabadoraFalsa.ultima = this;
  }

  start() {
    this.state = 'recording';
  }

  stop() {
    this.state = 'inactive';
    setTimeout(() => this.entregarYParar('final'), 0);
  }

  /** Se para sola (el auricular se ha caído): la pista acaba y la grabadora para por su cuenta. */
  pararseSola() {
    this.state = 'inactive';
    this.entregarYParar('hasta-aqui');
  }

  entregarYParar(contenido: string) {
    const trozo = new Event('dataavailable') as Event & { data: Blob };
    trozo.data = new Blob([contenido]);
    this.dispatchEvent(trozo);
    this.dispatchEvent(new Event('stop'));
  }
}

const pista = { stop: vi.fn() };
const pistas = [pista];

beforeEach(() => {
  vi.useFakeTimers();
  pista.stop.mockClear();
  vi.stubGlobal('MediaRecorder', GrabadoraFalsa);
  vi.stubGlobal('navigator', {
    mediaDevices: { getUserMedia: () => Promise.resolve({ getTracks: () => pistas }) },
  });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

async function texto(g: Grabacion) {
  return await g.blob.text();
}

describe('Grabador', () => {
  it('parar() entrega lo grabado cuando llega el stop y suelta el micrófono', async () => {
    const grabador = new Grabador();
    await grabador.empezar();
    const fin = grabador.parar();
    await vi.advanceTimersByTimeAsync(0);
    const g = await fin;
    expect(await texto(g)).toBe('final');
    expect(g.duracionSeg).toBeGreaterThanOrEqual(1);
    expect(pista.stop).toHaveBeenCalled();
    expect(await grabador.parar()).toBe(g);
  });

  it('si se para sola, avisa con lo grabado, para el reloj y parar() lo devuelve igual', async () => {
    const grabador = new Grabador();
    const tope = vi.fn();
    const tick = vi.fn();
    grabador.onTope = tope;
    grabador.onTick = tick;
    await grabador.empezar();
    GrabadoraFalsa.ultima!.pararseSola();

    expect(tope).toHaveBeenCalledTimes(1);
    const g = tope.mock.calls[0]?.[0] as Grabacion;
    expect(await texto(g)).toBe('hasta-aqui');
    expect(pista.stop).toHaveBeenCalled();
    tick.mockClear();
    await vi.advanceTimersByTimeAsync(2000);
    expect(tick).not.toHaveBeenCalled();
    expect(await grabador.parar()).toBe(g);
  });

  it('si ya está parada y el stop no llega, parar() se resuelve igual con lo que haya', async () => {
    const grabador = new Grabador();
    await grabador.empezar();
    const falsa = GrabadoraFalsa.ultima!;
    falsa.state = 'inactive'; // parada sin evento (un error en un navegador que no manda stop)
    const resuelta = vi.fn();
    void grabador.parar().then(resuelta);
    await vi.advanceTimersByTimeAsync(999);
    expect(resuelta).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(resuelta).toHaveBeenCalledTimes(1);
    expect(pista.stop).toHaveBeenCalled();
  });

  it('un error con la grabadora ya parada también acaba la grabación', async () => {
    const grabador = new Grabador();
    const tope = vi.fn();
    grabador.onTope = tope;
    await grabador.empezar();
    const falsa = GrabadoraFalsa.ultima!;
    falsa.state = 'inactive';
    falsa.dispatchEvent(new Event('error'));
    await vi.advanceTimersByTimeAsync(1000);
    expect(tope).toHaveBeenCalledTimes(1);
  });

  it('cancelar() no avisa de nada aunque luego llegue el stop', async () => {
    const grabador = new Grabador();
    const tope = vi.fn();
    grabador.onTope = tope;
    await grabador.empezar();
    grabador.cancelar();
    await vi.advanceTimersByTimeAsync(10);
    expect(tope).not.toHaveBeenCalled();
    expect(pista.stop).toHaveBeenCalled();
  });
});
