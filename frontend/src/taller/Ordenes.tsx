// «Tengo que hacer»: las órdenes que despacho manda al taller, en el orden de
// la maqueta (las nuevas primero, luego por prioridad, la más antigua antes) y
// las hechas debajo. La lista se vuelve a pedir cuando cambian los números de
// la bandeja, así que una orden que llega con la pantalla abierta aparece sola.
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import FotoModelo from '../comun/FotoModelo';
import { Icono } from '../comun/iconos';
import { estaCerrada, estadoOrden, fotoDelModelo, ordenarOrdenes, type ModificacionResumen } from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { useBandeja } from '../comun/useBandeja';

function Tarjeta({ orden }: { orden: ModificacionResumen }) {
  const navegar = useNavigate();
  const estado = estadoOrden(orden.estado);
  // La clase de la maqueta (nueva, vista, hecha): las cerradas se ven más apagadas.
  const clases = `orden ${estaCerrada(orden.estado) ? 'hecha' : estado.pill}`;
  return (
    <button className={clases} type="button" data-p={orden.prioridad ?? undefined} onClick={() => navegar(`/ordenes/${orden.id}`)}>
      <span className="franja" />
      <FotoModelo modelo={fotoDelModelo(orden)} />
      <span className="cuerpo">
        <span className="tit">{orden.titulo ?? orden.tipoCambioTexto}</span>
        <span className="meta">
          <span>
            <b>{orden.modeloReferencia}</b> · {orden.modeloNombre}
          </span>
        </span>
        <span className="meta">
          <span className={`pill ${estado.pill}`}>{estado.texto}</span>
          {orden.vencida && <span className="pill vencida">Vencida</span>}
          {hace(orden.creadaEn)}
          {orden.creadaPor && ` · de ${orden.creadaPor}`}
        </span>
      </span>
    </button>
  );
}

export default function Ordenes() {
  const { resumen } = useBandeja();
  const [ordenes, setOrdenes] = useState<ModificacionResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    api<ModificacionResumen[]>('/api/modificaciones?direccion=DESPACHO_A_TALLER')
      .then((lista) => {
        if (esta !== peticion.current) return;
        setOrdenes(lista);
        setError(null);
      })
      .catch((e: unknown) => esta === peticion.current && setError(e instanceof ErrorApi ? e.message : 'No se han podido cargar las órdenes'));
  }, []);

  // Al entrar y cada vez que la bandeja dice que algo ha cambiado (también cuando una orden pasa a vencida).
  const cambio = resumen ? `${resumen.nuevas}·${resumen.pendientes}·${resumen.vencidas}` : '';
  useEffect(cargar, [cargar, cambio]);

  if (error && !ordenes) {
    return (
      <div className="vacio">
        <b>{error}</b>
        <button className="boton" type="button" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!ordenes) return null;
  const { pendientes, hechas } = ordenarOrdenes(ordenes);

  return (
    <>
      <h2 className="saludo">Tengo que hacer</h2>
      <p className="sub">Las nuevas primero, luego por prioridad. La franja roja es prioridad alta.</p>
      {pendientes.length > 0 ? (
        <div className="lista">
          {pendientes.map((o) => (
            <Tarjeta key={o.id} orden={o} />
          ))}
        </div>
      ) : (
        <div className="vacio">
          <Icono nombre="ok" />
          <b>No tienes nada pendiente</b>
          <span>Cuando despacho mande una orden, sonará y vibrará.</span>
        </div>
      )}
      {hechas.length > 0 && (
        <>
          <div className="fila-titulo">
            <h2>Hechas</h2>
          </div>
          <div className="lista">
            {hechas.map((o) => (
              <Tarjeta key={o.id} orden={o} />
            ))}
          </div>
        </>
      )}
    </>
  );
}
