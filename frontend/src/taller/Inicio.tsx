// Inicio del taller: el saludo (cambia con la hora, ver comun/saludo.ts), las dos cosas que se vienen a hacer (mirar lo
// que pide despacho y avisar de un cambio) y, debajo, los modelos. Arriba, la
// primera vez, la tarjeta para activar los avisos al móvil.
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import ActivarAvisos from '../comun/ActivarAvisos';
import { Icono } from '../comun/iconos';
import { apuntarDivertida, conNombre, elegirSaludo, type HistorialDivertidas } from '../comun/saludo';
import { useSesion } from '../comun/sesion';
import { useBandeja } from '../comun/useBandeja';
import RejillaModelos from './RejillaModelos';

// Las divertidas que han salido en esta tablet, para el tope de 6 en 24 horas.
const DIVERTIDAS_VISTAS = 'modelos.saludo.divertidas';

function leerHistorial(): HistorialDivertidas {
  try {
    const guardado = localStorage.getItem(DIVERTIDAS_VISTAS);
    const h = guardado ? (JSON.parse(guardado) as Partial<HistorialDivertidas>) : {};
    return { veces: Array.isArray(h.veces) ? h.veces.filter((t) => typeof t === 'number') : [], ultima: h.ultima ?? null };
  } catch {
    return { veces: [], ultima: null };
  }
}

function guardarHistorial(historial: HistorialDivertidas) {
  try {
    localStorage.setItem(DIVERTIDAS_VISTAS, JSON.stringify(historial));
  } catch {
    // Sin almacenamiento (ventana privada) no hay tope: sale 1 de cada 4 igualmente.
  }
}

/** Un saludo nuevo cada vez que se entra en el inicio; no cambia mientras se está en él. */
function useSaludo(nombre: string | undefined): string {
  const [eleccion] = useState(() => elegirSaludo(new Date(), leerHistorial()));
  // Se apunta una sola vez, aunque el modo estricto repita el efecto en desarrollo.
  const apuntada = useRef(false);
  useEffect(() => {
    if (!eleccion.divertida || apuntada.current) return;
    apuntada.current = true;
    guardarHistorial(apuntarDivertida(leerHistorial(), eleccion.plantilla, new Date()));
  }, [eleccion]);
  return conNombre(eleccion.plantilla, nombre);
}

export default function Inicio() {
  const navegar = useNavigate();
  const { sesion } = useSesion();
  const saludo = useSaludo(sesion?.nombre);
  // Las órdenes de despacho sin abrir, de la consulta de la bandeja que lleva el marco.
  const { resumen } = useBandeja();
  const nuevas = resumen?.nuevas ?? 0;

  return (
    <>
      <h2 className="saludo">{saludo}</h2>
      <ActivarAvisos motivo="Para que suene cuando despacho mande una orden" />
      <div className="losas">
        <button className="losa" type="button" onClick={() => navegar('/ordenes')}>
          <span className={nuevas ? 'badge' : 'badge cero'}>{nuevas}</span>
          <Icono nombre="bandeja" />
          <span>
            <span className="t">Bandeja de Entrada</span>
          </span>
        </button>
        <button className="losa primaria" type="button" onClick={() => navegar('/cambio')}>
          <Icono nombre="mic" />
          <span>
            <span className="t">Crear Modificación</span>
            <br />
            <span className="m">Avisar con fotos y voz</span>
          </span>
        </button>
      </div>
      <div className="fila-titulo">
        <h2>Modelos</h2>
        <button className="chip" type="button" onClick={() => navegar('/escanear')}>
          <Icono nombre="qr" /> Escanear
        </button>
      </div>
      <RejillaModelos onElegir={(id) => navegar(`/modelos/${id}`)} />
    </>
  );
}
