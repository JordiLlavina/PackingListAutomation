// Los iconos de la app: los de la maqueta (docs/Modelos/maqueta-tablet.html),
// trazo sencillo sobre 24×24 y del color del texto. Se pintan con
// <Icono nombre="mic" />; el tamaño lo pone la hoja de estilos según dónde
// vaya (.losa svg, .nav button svg...), y si no, 24×24.

const I = {
  casa: '<path d="M3 11 12 3l9 8"/><path d="M5 10v10h5v-6h4v6h5V10"/>',
  bandeja: '<path d="M3 13h5l2 3h4l2-3h5"/><path d="M5 5h14l2 8v6H3v-6z"/>',
  lista: '<path d="M8 6h13M8 12h13M8 18h13"/><circle cx="4" cy="6" r="1.2"/><circle cx="4" cy="12" r="1.2"/><circle cx="4" cy="18" r="1.2"/>',
  rejilla: '<rect x="3" y="3" width="8" height="8" rx="2"/><rect x="13" y="3" width="8" height="8" rx="2"/><rect x="3" y="13" width="8" height="8" rx="2"/><rect x="13" y="13" width="8" height="8" rx="2"/>',
  qr: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3h-3zM19 14h2M14 19h2M19 19h2v2"/>',
  mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3M9 21h6"/>',
  cam: '<path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/>',
  play: '<path d="M7 4v16l13-8z" fill="currentColor" stroke="none"/>',
  pausa: '<path d="M7 4h4v16H7zM13 4h4v16h-4z" fill="currentColor" stroke="none"/>',
  check: '<path d="M4 12.5 10 18.5 20 6.5"/>',
  atras: '<path d="M15 5l-7 7 7 7"/>',
  mas: '<path d="M12 5v14M5 12h14"/>',
  duda: '<path d="M9 9a3 3 0 1 1 4.5 2.6c-1 .6-1.5 1.2-1.5 2.4"/><circle cx="12" cy="18" r=".8" fill="currentColor"/><circle cx="12" cy="12" r="9.5"/>',
  pdf: '<path d="M6 3h8l5 5v13H6z"/><path d="M14 3v5h5"/><path d="M9 17v-5h2a1.5 1.5 0 0 1 0 3H9"/>',
  piel: '<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M3 12c3-3 5 3 8 0s5 3 10 0"/>',
  forro: '<rect x="4" y="8" width="13" height="12" rx="2"/><path d="M8 8V6a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-1"/>',
  herraje: '<rect x="3" y="8" width="18" height="8" rx="4"/><path d="M9 8v8M15 8v8"/>',
  costura: '<path d="M3 18 21 6" stroke-dasharray="3 3"/><path d="m16 4 4 4-2 2-4-4z"/>',
  medidas: '<rect x="2" y="8" width="20" height="8" rx="1"/><path d="M6 8v3M10 8v4M14 8v3M18 8v4"/>',
  patron: '<circle cx="6" cy="18" r="3"/><circle cx="18" cy="18" r="3"/><path d="M8.5 16 20 4M15.5 16 4 4"/>',
  otro: '<circle cx="6" cy="12" r="1.5" fill="currentColor"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/><circle cx="18" cy="12" r="1.5" fill="currentColor"/>',
  campana: '<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
  ok: '<circle cx="12" cy="12" r="9.5"/><path d="m8 12.5 3 3 5-6"/>',
  salir: '<path d="M10 5H5v14h5M14 8l4 4-4 4M8 12h10"/>',
} as const;

export type NombreIcono = keyof typeof I;

export function Icono({ nombre, className }: { nombre: NombreIcono; className?: string }) {
  return (
    <svg
      className={className}
      width={24}
      height={24}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      // Trazos fijos escritos por nosotros, nunca texto que venga de fuera.
      dangerouslySetInnerHTML={{ __html: I[nombre] }}
    />
  );
}

/** Si un nombre que llega de la API (los tipos de cambio) es uno de nuestros iconos. */
export function esNombreIcono(nombre: string): nombre is NombreIcono {
  return Object.prototype.hasOwnProperty.call(I, nombre);
}
