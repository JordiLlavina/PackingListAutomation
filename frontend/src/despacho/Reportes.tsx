// Reportes de despacho (/app/reportes): lo que no es una alerta pero conviene
// poder mirar (decisión del usuario). Cuántos días lleva sin hacerse la
// primera de la cola (la siguiente que hará el taller), lo mismo de cada
// urgente, y las órdenes que ya han pasado su fecha objetivo. Una orden puede
// llevar días porque de verdad cuesta hacerla, así que esto no avisa a nadie:
// se lee aquí cuando se quiere.
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import FechaObjetivo from '../comun/FechaObjetivo';
import FotoModelo from '../comun/FotoModelo';
import { Icono } from '../comun/iconos';
import { fotoDelModelo, informeCola, puesto, tituloVisible, type ModificacionResumen } from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { useBandeja } from '../comun/useBandeja';

function dias(n: number): string {
  if (n === 0) return 'desde hoy';
  return n === 1 ? '1 día' : `${n} días`;
}

function Fila({ m, texto }: { m: ModificacionResumen; texto: string }) {
  return (
    <li className="fila-reporte" data-urgente={m.urgente ? 'true' : undefined}>
      <FotoModelo modelo={fotoDelModelo(m)} />
      <span className="cuerpo">
        <Link className="tit" to={`/bandeja/${m.id}?pestana=despacho`}>
          {tituloVisible(m)}
        </Link>
        <span className="meta">
          {m.posicionCola !== null && <b>{puesto(m.posicionCola)} · </b>}
          {m.modeloReferencia} · {m.modeloNombre} · {m.cliente} · creada {hace(m.creadaEn)}
        </span>
        <span className="etiquetas">
          {m.urgente && <span className="pill urgente">Urgente</span>}
          <FechaObjetivo fecha={m.fechaObjetivo} />
        </span>
      </span>
      <span className="dias">{texto}</span>
    </li>
  );
}

export default function Reportes() {
  const { resumen } = useBandeja();
  const [cola, setCola] = useState<ModificacionResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const cargar = useCallback(() => {
    api<ModificacionResumen[]>('/api/modificaciones/cola')
      .then((c) => {
        setCola(c);
        setError(null);
      })
      .catch((e: unknown) => setError(e instanceof ErrorApi ? e.message : 'No se ha podido cargar la cola'));
  }, []);

  // Al entrar y cuando cambian los números de la bandeja (una orden hecha, una urgente nueva).
  const cambio = resumen ? `${resumen.pendientes}|${resumen.urgentes}|${resumen.nuevas}` : '';
  useEffect(cargar, [cargar, cambio]);

  if (error && !cola) {
    return (
      <div className="vacio">
        <b>{error}</b>
        <button className="boton" type="button" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!cola) return null;
  const informe = informeCola(cola);

  return (
    <section className="pantalla-reportes" aria-label="Reportes">
      <div className="cabecera-cola">
        <div>
          <h2>Reportes</h2>
          <p className="ayuda">
            Cuánto lleva cada cosa sin hacerse. Es información, no una alerta: una orden puede llevar días porque de verdad cuesta. {informe.abiertas === 1 ? '1 orden abierta' : `${informe.abiertas} órdenes abiertas`} en la cola.
          </p>
        </div>
        <Link className="boton" to="/cola">
          Ver la cola
        </Link>
      </div>

      <h3>La siguiente del taller</h3>
      {informe.primera ? (
        <ul className="lista-reporte">
          <Fila m={informe.primera.orden} texto={`${dias(informe.primera.dias)} sin hacerse`} />
        </ul>
      ) : (
        <div className="vacio">
          <Icono nombre="ok" />
          <b>El taller no tiene ninguna orden pendiente</b>
        </div>
      )}

      <h3>Urgentes</h3>
      {informe.urgentes.length === 0 ? (
        <p className="ayuda">Ninguna orden urgente.</p>
      ) : (
        <ul className="lista-reporte">
          {informe.urgentes.map((u) => (
            <Fila key={u.orden.id} m={u.orden} texto={`${dias(u.dias)} sin hacerse`} />
          ))}
        </ul>
      )}

      <h3>Con la fecha objetivo pasada</h3>
      {informe.fechaPasada.length === 0 ? (
        <p className="ayuda">Ninguna orden ha pasado su fecha objetivo.</p>
      ) : (
        <ul className="lista-reporte">
          {informe.fechaPasada.map((f) => (
            <Fila key={f.orden.id} m={f.orden} texto={f.diasPasados === 1 ? 'pasó ayer' : `pasó hace ${f.diasPasados} días`} />
          ))}
        </ul>
      )}
    </section>
  );
}
