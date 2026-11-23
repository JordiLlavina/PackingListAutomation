// Las fotos de un modelo en el orden en que salen en la orden de corte y en
// el Word de fotos: se cambian de sitio arrastrándolas (se suelta una encima
// de otra y se queda en su sitio, como en la Lista de Prioridades) o con las
// flechas, para quien no arrastra. La primera es la principal y lo dice. Lo
// usan la galería de la ficha y la elección de imágenes de un PDF; quien la
// pone decide qué hacer con el orden nuevo.
import { useState, type DragEvent } from 'react';
import { soltarSobre } from '../comun/modificaciones';

export type FotoOrdenable = {
  clave: string;
  src: string;
  alt: string;
  /** Una línea debajo: de dónde sale («Ya guardada», «página 2», el nombre del fichero). */
  nota?: string;
  /** Si se puede quitar desde aquí, lo que hace el botón. */
  onQuitar?: () => void;
};

type Props = {
  fotos: FotoOrdenable[];
  onOrdenar: (claves: string[]) => void;
  /** Mientras se guarda: no se deja mover nada. */
  ocupada?: boolean;
};

export default function FotosOrdenables({ fotos, onOrdenar, ocupada = false }: Props) {
  const [arrastrada, setArrastrada] = useState<string | null>(null);
  const [encima, setEncima] = useState<string | null>(null);
  const claves = fotos.map((f) => f.clave);

  const mover = (clave: string, paso: -1 | 1) => {
    const vecina = claves[claves.indexOf(clave) + paso];
    if (vecina === undefined || !claves.includes(clave)) return;
    onOrdenar(soltarSobre(claves, clave, vecina));
  };

  const empezar = (e: DragEvent, clave: string) => {
    if (ocupada) return e.preventDefault();
    setArrastrada(clave);
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', clave);
  };
  const sobre = (e: DragEvent, clave: string) => {
    if (arrastrada === null) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (encima !== clave) setEncima(clave);
  };
  const soltar = (e: DragEvent, clave: string) => {
    e.preventDefault();
    const origen = arrastrada;
    setArrastrada(null);
    setEncima(null);
    if (origen === null || ocupada) return;
    const nuevas = soltarSobre(claves, origen, clave);
    if (nuevas !== claves) onOrdenar(nuevas);
  };
  const acabar = () => {
    setArrastrada(null);
    setEncima(null);
  };

  return (
    <ol className="miniaturas fotos-ordenables" aria-busy={ocupada || undefined}>
      {fotos.map((f, i) => (
        <li
          key={f.clave}
          className={`${arrastrada === f.clave ? 'arrastrando' : ''}${encima === f.clave && arrastrada !== f.clave ? ' encima' : ''}`.trim() || undefined}
          draggable={!ocupada}
          onDragStart={(e) => empezar(e, f.clave)}
          onDragOver={(e) => sobre(e, f.clave)}
          onDrop={(e) => soltar(e, f.clave)}
          onDragEnd={acabar}
        >
          <span className="puesto-foto">{i + 1}</span>
          <img src={f.src} alt={f.alt} loading="lazy" draggable={false} />
          {f.onQuitar && (
            <button className="quitar" type="button" disabled={ocupada} aria-label={`Quitar ${f.alt}`} title="Quitar" onClick={f.onQuitar}>
              ×
            </button>
          )}
          <div className="pie-foto">
            {i === 0 ? <span className="principal">Principal</span> : <span className="meta">{f.nota ?? ''}</span>}
            <span className="flechas">
              <button className="chip" type="button" disabled={ocupada || i === 0} aria-label={`Mover ${f.alt} antes`} onClick={() => mover(f.clave, -1)}>
                ◀
              </button>
              <button
                className="chip"
                type="button"
                disabled={ocupada || i === fotos.length - 1}
                aria-label={`Mover ${f.alt} después`}
                onClick={() => mover(f.clave, 1)}
              >
                ▶
              </button>
            </span>
          </div>
          {i === 0 && f.nota && <span className="meta">{f.nota}</span>}
        </li>
      ))}
    </ol>
  );
}
