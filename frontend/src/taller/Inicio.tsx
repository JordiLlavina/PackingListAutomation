// Inicio del taller: el saludo (cambia con la hora, ver comun/saludo.ts), las dos cosas que se vienen a hacer (mirar lo
// que pide despacho y avisar de un cambio) y, debajo, los modelos. Arriba, la
// primera vez, la tarjeta para activar los avisos al móvil.
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import ActivarAvisos from '../comun/ActivarAvisos';
import { Icono } from '../comun/iconos';
import { apuntarSaludo, conNombre, elegirSaludo, HISTORIAL_VACIO, type HistorialSaludo } from '../comun/saludo';
import { useSesion } from '../comun/sesion';
import { useBandeja } from '../comun/useBandeja';
import RejillaModelos from './RejillaModelos';

// Lo que ya ha salido en esta tablet: las frases de franja de hoy y la última
// divertida (ver comun/saludo.ts).
const SALUDOS_VISTOS = 'modelos.saludo';
// La clave de la primera versión (tope de divertidas), que ya no se usa.
const CLAVE_VIEJA = 'modelos.saludo.divertidas';

function leerHistorial(): HistorialSaludo {
  try {
    localStorage.removeItem(CLAVE_VIEJA);
    const guardado = localStorage.getItem(SALUDOS_VISTOS);
    const h = guardado ? (JSON.parse(guardado) as Partial<HistorialSaludo>) : {};
    const franjas =
      h.franjas && typeof h.franjas === 'object'
        ? Object.fromEntries(Object.entries(h.franjas).filter(([, dia]) => typeof dia === 'string'))
        : {};
    return {
      ultimaDivertida: typeof h.ultimaDivertida === 'string' ? h.ultimaDivertida : null,
      ultimaFranja: typeof h.ultimaFranja === 'number' ? h.ultimaFranja : null,
      franjas,
    };
  } catch {
    return HISTORIAL_VACIO;
  }
}

function guardarHistorial(historial: HistorialSaludo) {
  try {
    localStorage.setItem(SALUDOS_VISTOS, JSON.stringify(historial));
  } catch {
    // Sin almacenamiento (ventana privada) una frase de franja puede repetirse: no pasa nada.
  }
}

/** Un saludo nuevo cada vez que se entra en el inicio; no cambia mientras se está en él. */
function useSaludo(nombre: string | undefined): string {
  const [eleccion] = useState(() => elegirSaludo(new Date(), leerHistorial()));
  // Se apunta una sola vez, aunque el modo estricto repita el efecto en desarrollo.
  const apuntada = useRef(false);
  useEffect(() => {
    if (eleccion.tipo === 'hola' || apuntada.current) return;
    apuntada.current = true;
    guardarHistorial(apuntarSaludo(leerHistorial(), eleccion, new Date()));
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
      <h2 className="saludo inicio">{saludo}</h2>
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
