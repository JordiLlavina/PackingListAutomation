// Lo que lleva el QR de la etiqueta de un patrón: la dirección de la ficha del
// modelo (`https://…/m/123`). Se acepta también la ruta sola y el número a
// secas, que es lo que se teclearía o pegaría a mano.

/** El id del modelo que dice un texto leído de un QR, o null si no es de una etiqueta de patrón. */
export function idDeModelo(texto: string): number | null {
  const limpio = texto.trim();
  const numero = /^\d+$/.exec(limpio)?.[0] ?? /^(?:[a-z][a-z0-9+.-]*:\/\/[^/?#]+)?\/m\/(\d+)\/?(?:[?#].*)?$/i.exec(limpio)?.[1];
  if (numero === undefined) return null;
  const id = Number(numero);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
