// La nota de voz: el micrófono de la tablet con MediaRecorder. Chrome graba
// WebM/Opus, que es lo que la transcripción acepta tal cual; Safari, MP4. Se
// para sola a los 3 minutos, que es lo más que admite el servidor.
//
// La grabadora también se puede parar sola sin que nadie la toque: se cae el
// auricular, el sistema le da el micrófono a una llamada... Entonces se queda
// lo grabado hasta ahí, como al llegar al tope, y parar() lo devuelve igual:
// una promesa que no se resuelve nunca dejaría la pantalla «grabando» para
// siempre.

export type Grabacion = { blob: Blob; duracionSeg: number; mime: string };

const FORMATOS = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];

/**
 * Lo que se espera al evento `stop` si la grabadora ya está parada cuando se
 * pide parar (se acaba de parar sola y el evento todavía no ha llegado, o un
 * navegador que tras un error no lo manda). El último trozo de audio llega
 * justo antes de ese evento, así que no se resuelve en el acto.
 */
const ESPERA_STOP_MS = 1000;

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
  /**
   * Cuando se ha parado sola, con lo grabado: al llegar al tope o porque el
   * micrófono se ha ido (auricular, otra aplicación). No salta si la para
   * parar() o cancelar().
   */
  onTope: ((grabacion: Grabacion) => void) | null = null;

  private flujo: MediaStream | null = null;
  private grabadora: MediaRecorder | null = null;
  private trozos: Blob[] = [];
  private inicio = 0;
  private reloj: ReturnType<typeof setInterval> | null = null;
  private fin: Promise<Grabacion> | null = null;
  /** El que resuelve el `fin` que ha pedido parar(), en cuanto llega el `stop`. */
  private resolver: ((g: Grabacion) => void) | null = null;
  /** La duración fijada al pedir parar (lo que tarda en llegar el `stop` no cuenta). */
  private duracionAlParar: number | null = null;
  private espera: ReturnType<typeof setTimeout> | null = null;

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
    this.resolver = null;
    this.duracionAlParar = null;
    grabadora.addEventListener('dataavailable', (e) => {
      if (e.data.size > 0) this.trozos.push(e.data);
    });
    // Se escuchan desde el principio, no al pedir parar: así una parada que
    // no ha pedido nadie también acaba la grabación y para el reloj.
    grabadora.addEventListener('stop', () => this.terminar(grabadora));
    grabadora.addEventListener('error', () => this.esperarStop(grabadora));
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
    this.pararReloj();
    this.duracionAlParar = this.duracion();
    this.fin = new Promise<Grabacion>((resolver) => {
      this.resolver = resolver;
    });
    if (grabadora.state === 'inactive') {
      this.esperarStop(grabadora);
    } else {
      grabadora.stop();
    }
    return this.fin;
  }

  /** Si se sale de la pantalla grabando: se tira lo grabado y se suelta el micrófono. */
  cancelar() {
    this.pararReloj();
    if (this.espera) clearTimeout(this.espera);
    this.espera = null;
    const grabadora = this.grabadora;
    // Primero se olvida la grabadora: el `stop` que llegue ya no tiene nada que acabar.
    this.grabadora = null;
    if (grabadora && grabadora.state !== 'inactive') grabadora.stop();
    this.flujo?.getTracks().forEach((pista) => pista.stop());
    this.flujo = null;
    this.resolver = null;
    this.onTick = null;
    this.onTope = null;
  }

  /** Si el `stop` no llega a tiempo, se acaba con los trozos que haya. */
  private esperarStop(grabadora: MediaRecorder) {
    if (this.espera || grabadora !== this.grabadora) return;
    this.espera = setTimeout(() => this.terminar(grabadora), ESPERA_STOP_MS);
  }

  /**
   * Acaba la grabación (una sola vez): suelta el micrófono y entrega lo
   * grabado a quien lo pidió con parar() o, si se ha parado sola, a onTope.
   */
  private terminar(grabadora: MediaRecorder) {
    if (grabadora !== this.grabadora) return;
    this.pararReloj();
    if (this.espera) clearTimeout(this.espera);
    this.espera = null;
    const mime = grabadora.mimeType || this.trozos[0]?.type || 'audio/webm';
    const grabacion: Grabacion = {
      blob: new Blob(this.trozos, { type: mime }),
      duracionSeg: this.duracionAlParar ?? this.duracion(),
      mime,
    };
    this.flujo?.getTracks().forEach((pista) => pista.stop());
    this.flujo = null;
    this.grabadora = null;
    const resolver = this.resolver;
    this.resolver = null;
    if (resolver) {
      resolver(grabacion);
    } else {
      // Nadie la ha parado: se queda como la grabación de parar() y se avisa.
      this.fin = Promise.resolve(grabacion);
      this.onTope?.(grabacion);
    }
  }

  private pararReloj() {
    if (this.reloj) clearInterval(this.reloj);
    this.reloj = null;
  }

  /** Segundos grabados para el servidor: entero, de 1 al tope. */
  private duracion(): number {
    return Math.min(this.tope, Math.max(1, Math.round(this.segundos(true))));
  }

  private segundos(exacto = false): number {
    const s = (performance.now() - this.inicio) / 1000;
    return exacto ? s : Math.floor(s);
  }
}
