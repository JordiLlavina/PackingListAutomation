// Lo que se calcula para la hoja de etiquetas de patrones (despacho): qué
// modelos pide la dirección, cómo se reparten en hojas A4 (una por hoja) y a qué
// tamaño va el nombre según lo largo que sea. Funciones puras, para poder
// probarlas sin navegador.

/** Etiquetas por hoja A4: una, a página entera. */
export const POR_HOJA = 1;

/** Las etiquetas repartidas en hojas de POR_HOJA; la última lleva las que sobren. */
export function hojasDe<T>(etiquetas: T[]): T[][] {
  const hojas: T[][] = [];
  for (let i = 0; i < etiquetas.length; i += POR_HOJA) hojas.push(etiquetas.slice(i, i + POR_HOJA));
  return hojas;
}

/** Los ids de `?ids=3,1,7`, en su orden y sin repetir; lo que no es un id se ignora. */
export function idsDe(parametro: string | null): number[] {
  if (!parametro) return [];
  const ids: number[] = [];
  for (const trozo of parametro.split(',')) {
    const texto = trozo.trim();
    if (!/^\d+$/.test(texto)) continue;
    const id = Number(texto);
    if (id > 0 && !ids.includes(id)) ids.push(id);
  }
  return ids;
}

export type TamanoNombre = 'nombre-grande' | 'nombre-medio' | 'nombre-pequeno' | 'nombre-minimo';

/**
 * El tamaño del nombre en la etiqueta, por su número de letras: el nombre es
 * lo más grande (56 pt) mientras quepa en una línea, y encoge cuando no. El
 * hueco es de 19 cm de ancho y dos líneas de alto; los umbrales se midieron
 * en la etiqueta antigua (9 cm, letra a la mitad), que guarda la misma
 * proporción, con nombres en mayúsculas, que es como los escriben los clientes.
 */
export function tamanoNombre(nombre: string): TamanoNombre {
  const letras = nombre.trim().replace(/\s+/g, ' ').length;
  if (letras <= 13) return 'nombre-grande';
  if (letras <= 18) return 'nombre-medio';
  if (letras <= 38) return 'nombre-pequeno';
  return 'nombre-minimo';
}

/** «9 etiquetas · 2 hojas». */
export function recuento(etiquetas: number): string {
  const hojas = Math.ceil(etiquetas / POR_HOJA);
  return `${etiquetas} ${etiquetas === 1 ? 'etiqueta' : 'etiquetas'} · ${hojas} ${hojas === 1 ? 'hoja' : 'hojas'}`;
}

function dosCifras(n: number): string {
  return String(n).padStart(2, '0');
}

/** El día de una fecha de la API (ISO sin zona, hora local) como `03/11/2026`. */
export function fechaDia(fechaIso: string): string {
  const f = new Date(fechaIso);
  return `${dosCifras(f.getDate())}/${dosCifras(f.getMonth() + 1)}/${f.getFullYear()}`;
}
