// El aviso de un cambio mientras se prepara: el modelo, las razones del cambio
// (una o varias), las fotos y la nota de voz. Vive por encima de las rutas del taller, así que
// salir del recorrido por la barra de abajo no lo pierde: sigue ahí hasta que
// se manda o se empieza otro con un modelo distinto. Las fotos y la nota se
// enseñan con URLs de objeto (blob:) que se sueltan al quitarlas.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { ModeloResumen } from '../../comun/modelos';
import type { AudioBorrador, FotoBorrador } from './reglas';

type Estado = {
  modelo: ModeloResumen | null;
  /** Las razones marcadas (claves del yml), en el orden en que se tocaron. */
  tipos: string[];
  fotos: FotoBorrador[];
  audio: AudioBorrador | null;
};

export type Borrador = Estado & {
  /** Elige el modelo; si es otro que el del borrador, empieza uno nuevo (lo de antes era de otro modelo). */
  elegirModelo: (modelo: ModeloResumen) => void;
  /** Marca una razón, o la desmarca si ya estaba. */
  alternarTipo: (tipo: string) => void;
  anadirFotos: (blobs: Blob[]) => void;
  quitarFoto: (indice: number) => void;
  /** La nota de voz nueva (la anterior se suelta) o null para tirarla. */
  ponerAudio: (audio: { blob: Blob; duracionSeg: number } | null) => void;
  /** Lo deja vacío, soltando las fotos y la nota. */
  reiniciar: () => void;
};

const VACIO: Estado = { modelo: null, tipos: [], fotos: [], audio: null };

const BorradorContexto = createContext<Borrador | null>(null);

function soltar(estado: Estado) {
  estado.fotos.forEach((f) => URL.revokeObjectURL(f.url));
  if (estado.audio) URL.revokeObjectURL(estado.audio.url);
}

export function BorradorProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>(VACIO);
  // El último estado, para soltar las URLs fuera de los setState (en modo
  // estricto React los llama dos veces y soltaría las de otro). Lo mantiene
  // al día `cambiar`, que es el único que toca el estado.
  const actual = useRef(estado);

  const cambiar = useCallback((nuevo: Estado) => {
    actual.current = nuevo;
    setEstado(nuevo);
  }, []);

  const reiniciar = useCallback(() => {
    soltar(actual.current);
    cambiar(VACIO);
  }, [cambiar]);

  const elegirModelo = useCallback(
    (modelo: ModeloResumen) => {
      const antes = actual.current;
      if (antes.modelo?.id === modelo.id) {
        cambiar({ ...antes, modelo });
      } else {
        soltar(antes);
        cambiar({ ...VACIO, modelo });
      }
    },
    [cambiar],
  );

  const alternarTipo = useCallback(
    (tipo: string) => {
      const antes = actual.current;
      const tipos = antes.tipos.includes(tipo) ? antes.tipos.filter((t) => t !== tipo) : [...antes.tipos, tipo];
      cambiar({ ...antes, tipos });
    },
    [cambiar],
  );

  const anadirFotos = useCallback(
    (blobs: Blob[]) => {
      const nuevas = blobs.map((blob) => ({ blob, url: URL.createObjectURL(blob) }));
      cambiar({ ...actual.current, fotos: [...actual.current.fotos, ...nuevas] });
    },
    [cambiar],
  );

  const quitarFoto = useCallback(
    (indice: number) => {
      const antes = actual.current;
      const foto = antes.fotos[indice];
      if (!foto) return;
      URL.revokeObjectURL(foto.url);
      cambiar({ ...antes, fotos: antes.fotos.filter((_, i) => i !== indice) });
    },
    [cambiar],
  );

  const ponerAudio = useCallback(
    (audio: { blob: Blob; duracionSeg: number } | null) => {
      const antes = actual.current;
      if (antes.audio) URL.revokeObjectURL(antes.audio.url);
      cambiar({ ...antes, audio: audio ? { ...audio, url: URL.createObjectURL(audio.blob) } : null });
    },
    [cambiar],
  );

  // Al cerrar la sesión (se desmonta el taller) se suelta todo.
  useEffect(() => () => soltar(actual.current), []);

  const valor = useMemo<Borrador>(
    () => ({ ...estado, elegirModelo, alternarTipo, anadirFotos, quitarFoto, ponerAudio, reiniciar }),
    [estado, elegirModelo, alternarTipo, anadirFotos, quitarFoto, ponerAudio, reiniciar],
  );
  return <BorradorContexto.Provider value={valor}>{children}</BorradorContexto.Provider>;
}

export function useBorrador(): Borrador {
  const borrador = useContext(BorradorContexto);
  if (!borrador) throw new Error('useBorrador fuera de BorradorProvider');
  return borrador;
}
