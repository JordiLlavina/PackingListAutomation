// La etiqueta de la fecha objetivo de una orden («Fecha: 11 noviembre»), con
// el color que le toca según lo cerca o lo pasado que esté el día
// (estiloFechaObjetivo). Es una descripción, no una orden: el taller ve la
// urgencia en el color sin que nadie le hable en imperativo (decisión del
// usuario). La misma en la bandeja y en la cola del despacho, en el detalle
// de una orden y en la Bandeja de Entrada del taller.
import { estiloFechaObjetivo } from './modificaciones';

export default function FechaObjetivo({ fecha, hoy }: { fecha: string | null; hoy?: Date }) {
  if (!fecha) return null;
  const e = estiloFechaObjetivo(fecha, hoy);
  return (
    <span className="pill fecha-objetivo" style={{ background: e.fondo, color: e.tinta }} title={`Fecha objetivo: ${e.detalle}`} data-dias={e.dias}>
      {e.texto}
    </span>
  );
}
