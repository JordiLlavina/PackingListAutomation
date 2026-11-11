// La tarjeta «Activar los avisos»: sale mientras el navegador no ha dicho ni
// sí ni no a las notificaciones y el servidor tiene push (claves VAPID). Con
// el permiso dado desaparece (y la suscripción se vuelve a mandar en silencio,
// ver push.ts). «Ahora no» se recuerda en este navegador.
//
// Si el aparato NO admite los avisos (el permiso está negado, o el navegador
// no tiene push: un iPhone con la web sin instalar, un navegador viejo) queda
// una línea fija que lo dice y cómo arreglarlo, sin «Entendido» (decisión del
// usuario): una tablet del taller que no suena es un problema que hay que
// arreglar, no un aviso que se cierra. Solo si el servidor tiene push: sin
// claves no hay nada que arreglar en la tablet.
import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { Icono } from './iconos';
import { activarPush, estadoPush, type ClavePublica, type EstadoPush } from './push';
import { toast } from './toast';

const DESCARTADA = 'modelos.activar-avisos.descartada';

function leerDescartada(): boolean {
  try {
    return localStorage.getItem(DESCARTADA) === '1';
  } catch {
    return false;
  }
}

function guardarDescartada() {
  try {
    localStorage.setItem(DESCARTADA, '1');
  } catch {
    // Sin almacenamiento (ventana privada): vuelve a salir la próxima vez.
  }
}

export default function ActivarAvisos({ motivo, compacta = false }: { motivo: string; compacta?: boolean }) {
  const [configurado, setConfigurado] = useState(false);
  const [estado, setEstado] = useState<EstadoPush>(() => estadoPush());
  const [descartada, setDescartada] = useState(leerDescartada);
  const [ocupado, setOcupado] = useState(false);
  const renovada = useRef(false);

  useEffect(() => {
    let vivo = true;
    api<ClavePublica>('/api/push/clave-publica')
      .then((r) => vivo && setConfigurado(r.configurado))
      .catch(() => {
        // Sin respuesta no se ofrece nada; la próxima vez que se entre se vuelve a preguntar.
      });
    return () => {
      vivo = false;
    };
  }, [estado]);

  // Con el permiso ya dado, la suscripción se vuelve a mandar al entrar (una vez).
  useEffect(() => {
    if (configurado && estado === 'activo' && !renovada.current) {
      renovada.current = true;
      void activarPush();
    }
  }, [configurado, estado]);

  if (!configurado || estado === 'activo') return null;

  if (estado === 'denegado' || estado === 'no-soportado') {
    return (
      <p className={compacta ? 'avisos-bloqueados compacta' : 'avisos-bloqueados'} role="status">
        <Icono nombre="campana" />
        <b>Este aparato no recibe los avisos.</b>
        {estado === 'denegado'
          ? 'Las notificaciones están bloqueadas en el navegador: hay que permitirlas en los ajustes del sitio.'
          : 'Este navegador no admite notificaciones: usa Chrome e instala la app desde su menú.'}
      </p>
    );
  }

  if (descartada) return null;

  const ahoraNo = () => {
    guardarDescartada();
    setDescartada(true);
  };

  const activar = async () => {
    setOcupado(true);
    const activo = await activarPush();
    setOcupado(false);
    setEstado(estadoPush());
    if (activo) toast('Avisos activados');
    else if (estadoPush() !== 'denegado') toast('No se han podido activar los avisos');
  };

  return (
    <div className={compacta ? 'activar-avisos compacta' : 'activar-avisos'} role="region" aria-label="Activar los avisos">
      <Icono nombre="campana" />
      <span className="texto">
        <b>Activar los avisos</b>
        <span>{motivo}</span>
      </span>
      <span className="botones">
        <button className="boton marca" type="button" disabled={ocupado} onClick={() => void activar()}>
          Activar
        </button>
        <button className="enlace" type="button" onClick={ahoraNo}>
          Ahora no
        </button>
      </span>
    </div>
  );
}
