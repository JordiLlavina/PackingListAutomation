// Las fotos se reducen en el navegador antes de subirlas: una foto de 12 MP
// de la tablet pesa varios megas y viajaría entera por la WiFi del taller,
// cuando el servidor la va a dejar en 1.600 px de todas formas (y la vuelve a
// reducir, así que esto es solo para no gastar red).

/** Las medidas de una imagen con su lado largo como mucho en `ladoMax`; una pequeña se queda como está. */
export function dimensionesReducidas(ancho: number, alto: number, ladoMax: number): { ancho: number; alto: number } {
  const largo = Math.max(ancho, alto);
  if (largo <= ladoMax) return { ancho, alto };
  const escala = ladoMax / largo;
  return { ancho: Math.round(ancho * escala), alto: Math.round(alto * escala) };
}

/**
 * La foto reducida a JPEG con su lado largo en `ladoMax`, ya derecha (se aplica
 * la orientación del EXIF, que la cámara de la tablet usa en vez de girar los
 * píxeles). Si el navegador no sabe abrirla (un HEIC en un navegador que no lo
 * decodifica), se devuelve el fichero tal cual y que el servidor responda.
 */
export async function reducirImagen(file: File, ladoMax = 1600): Promise<Blob> {
  let mapa: ImageBitmap;
  try {
    mapa = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    return file;
  }
  try {
    const { ancho, alto } = dimensionesReducidas(mapa.width, mapa.height, ladoMax);
    const lienzo = document.createElement('canvas');
    lienzo.width = ancho;
    lienzo.height = alto;
    const contexto = lienzo.getContext('2d');
    if (!contexto) return file;
    // JPEG no tiene transparencia: sin fondo, lo transparente de un PNG saldría negro.
    contexto.fillStyle = '#ffffff';
    contexto.fillRect(0, 0, ancho, alto);
    contexto.drawImage(mapa, 0, 0, ancho, alto);
    const reducida = await new Promise<Blob | null>((resolver) => lienzo.toBlob(resolver, 'image/jpeg', 0.85));
    return reducida ?? file;
  } finally {
    mapa.close();
  }
}
