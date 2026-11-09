// La nota de voz: el micrófono de la tablet con MediaRecorder. Chrome graba
// WebM/Opus, que es lo que la transcripción acepta tal cual; Safari, MP4. Se
// para sola a los 3 minutos, que es lo más que admite el servidor.

export type Grabacion = { blob: Blob; duracionSeg: number; mime: string };

const FORMATOS = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];

/** El primer formato que sabe grabar este navegador; vacío = el que elija él. */
function formatoSoportado(): string {
  if (typeof MediaRecorder.isTypeSupported !== 'function') return '';
  return FORMATOS.find((f) => MediaRecorder.isTypeSupported(f)) ?? '';
}

export class Grabador {
  /** Segundos a los que se para sola. */
  readonly tope = 180;
  /** Cada segundo que pasa grabando, con los que lleva. */
  onTick: ((seg: number) => void) | null = null;
  /** Cuando se ha parado sola al llegar al tope, con lo grabado. */
  onTope: ((grabacion: Grabacion) => void) | null = null;

  private flujo: MediaStream | null = null;
  private grabadora: MediaRecorder | null = null;
  private trozos: Blob[] = [];
  private inicio = 0;
  private reloj: ReturnType<typeof setInterval> | null = null;
  private fin: Promise<Grabacion> | null = null;

  /** Pide el micrófono y empieza a grabar. Falla (se rechaza) si no hay permiso o no hay micrófono. */
  async empezar(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      throw new Error('Este navegador no sabe grabar');
    }
    const flujo = await navigator.mediaDevices.getUserMedia({ audio: true });
    const formato = formatoSoportado();
    let grabadora: MediaRecorder;
    try {
      grabadora = formato ? new MediaRecorder(flujo, { mimeType: formato }) : new MediaRecorder(flujo);
    } catch (e) {
      flujo.getTracks().forEach((pista) => pista.stop());
      throw e;
    }
    this.flujo = flujo;
    this.grabadora = grabadora;
    this.trozos = [];
    this.fin = null;
    grabadora.addEventListener('dataavailable', (e) => {
      if (e.data.size > 0) this.trozos.push(e.data);
    });
    grabadora.start();
    this.inicio = performance.now();
    this.reloj = setInterval(() => {
      const seg = this.segundos();
      if (seg >= this.tope) {
        void this.parar().then((g) => this.onTope?.(g));
      } else {
        this.onTick?.(seg);
      }
    }, 250);
  }

  /** Para la grabación y suelta el micrófono. Llamarla dos veces da la misma grabación. */
  parar(): Promise<Grabacion> {
    if (this.fin) return this.fin;
    const grabadora = this.grabadora;
    if (!grabadora) return Promise.reject(new Error('No se está grabando'));
    if (this.reloj) clearInterval(this.reloj);
    this.reloj = null;
    const duracionSeg = Math.min(this.tope, Math.max(1, Math.round(this.segundos(true))));
    this.fin = new Promise<Grabacion>((resolver) => {
      grabadora.addEventListener(
        'stop',
        () => {
          const mime = grabadora.mimeType || this.trozos[0]?.type || 'audio/webm';
          this.flujo?.getTracks().forEach((pista) => pista.stop());
          this.flujo = null;
          this.grabadora = null;
          resolver({ blob: new Blob(this.trozos, { type: mime }), duracionSeg, mime });
        },
        { once: true },
      );
      grabadora.stop();
    });
    return this.fin;
  }

  /** Si se sale de la pantalla grabando: se tira lo grabado y se suelta el micrófono. */
  cancelar() {
    if (this.reloj) clearInterval(this.reloj);
    this.reloj = null;
    if (this.grabadora && this.grabadora.state !== 'inactive') this.grabadora.stop();
    this.flujo?.getTracks().forEach((pista) => pista.stop());
    this.flujo = null;
    this.grabadora = null;
    this.onTick = null;
    this.onTope = null;
  }

  private segundos(exacto = false): number {
    const s = (performance.now() - this.inicio) / 1000;
    return exacto ? s : Math.floor(s);
  }
}
