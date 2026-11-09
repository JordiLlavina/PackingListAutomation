// La tarjeta «Activar los avisos»: sale mientras el navegador no ha dicho ni
// sí ni no a las notificaciones y el servidor tiene push (claves VAPID). Con
// el permiso dado desaparece (y la suscripción se vuelve a mandar en silencio,
// ver push.ts); con el permiso negado queda una línea que lo dice, porque
// desde la app ya no se puede volver a preguntar. «Ahora no» se recuerda en
// este navegador.
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
    if (estado === 'no-soportado') return;
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

  if (!configurado || estado === 'no-soportado' || estado === 'activo' || descartada) return null;

  const ahoraNo = () => {
    guardarDescartada();
    setDescartada(true);
  };

  if (estado === 'denegado') {
    return (
      <p className={compacta ? 'avisos-bloqueados compacta' : 'avisos-bloqueados'}>
        <Icono nombre="campana" /> Los avisos están bloqueados en el navegador
        <button className="enlace" type="button" onClick={ahoraNo}>
          Entendido
        </button>
      </p>
    );
  }

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
