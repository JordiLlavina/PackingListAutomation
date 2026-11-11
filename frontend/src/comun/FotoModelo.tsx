// La foto de un modelo: la principal si la tiene y, si no, la silueta de la
// maqueta (bolso o cinturón) en un color sacado del propio modelo, para que
// dos modelos sin foto no se vean iguales en la rejilla. El tamaño lo pone la
// hoja de estilos según dónde vaya (.tarjeta-modelo .foto, .ficha .foto...).
import { fotoUrl, type ModeloResumen } from './modelos';

type Props = {
  modelo: Pick<ModeloResumen, 'id' | 'referencia' | 'tipo' | 'fotoPrincipalId'>;
  className?: string;
};

/** Un matiz de 0 a 359 que sale siempre igual para el mismo modelo. */
function matiz(texto: string): number {
  let h = 0;
  for (let i = 0; i < texto.length; i++) h = (h * 31 + texto.charCodeAt(i)) % 360;
  return h;
}

export default function FotoModelo({ modelo, className = 'foto' }: Props) {
  if (modelo.fotoPrincipalId !== null) {
    return <img className={className} src={fotoUrl(modelo.fotoPrincipalId)} alt="" loading="lazy" />;
  }
  const h = matiz(`${modelo.id}·${modelo.referencia}`);
  const s = 45;
  const fondo = `hsl(${h} ${Math.round(s * 0.35)}% 92%)`;
  const piel = `hsl(${h} ${s}% 38%)`;
  const oscuro = `hsl(${h} ${s}% 26%)`;
  return (
    <svg className={className} viewBox="0 0 100 100" role="img" aria-label={`Foto de ${modelo.referencia}`} style={{ background: fondo }}>
      {modelo.tipo === 'CINTURON' ? (
        <>
          <rect x="6" y="42" width="88" height="16" rx="6" fill={piel} />
          <rect x="62" y="36" width="22" height="28" rx="5" fill="none" stroke={oscuro} strokeWidth="5" />
          <rect x="72" y="36" width="4" height="28" fill={oscuro} />
          <circle cx="24" cy="50" r="2.5" fill={oscuro} />
          <circle cx="38" cy="50" r="2.5" fill={oscuro} />
        </>
      ) : (
        <>
          <rect x="18" y="38" width="64" height="48" rx="10" fill={piel} />
          <path d="M34 40c0-14 32-14 32 0" fill="none" stroke={oscuro} strokeWidth="6" strokeLinecap="round" />
          <rect x="18" y="52" width="64" height="6" fill={oscuro} opacity=".5" />
          <circle cx="50" cy="66" r="4" fill={oscuro} />
        </>
      )}
    </svg>
  );
}
