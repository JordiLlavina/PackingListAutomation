// El logo de Puntotres (los tres cuadrados del icono de la app vieja,
// static/icono-web-180.png), redibujado en vectorial para que tome el color de
// la app (var(--accent) en la hoja) y siga al modo oscuro. Medidas sacadas del
// PNG de 180 px.
export function LogoPuntotres({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="8 30 166 122" fill="currentColor" aria-hidden="true">
      <rect x="8" y="30" width="70" height="73" rx="15" />
      <rect x="38" y="110" width="42" height="41" rx="10" />
      <path d="M104 58H174V130A22 22 0 0 1 152 152H100A16 16 0 0 1 84 136V78A20 20 0 0 1 104 58Z" />
    </svg>
  );
}
