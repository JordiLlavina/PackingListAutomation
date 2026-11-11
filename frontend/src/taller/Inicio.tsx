// Inicio del taller: el saludo, las dos cosas que se vienen a hacer (mirar lo
// que pide despacho y avisar de un cambio) y, debajo, los modelos. Arriba, la
// primera vez, la tarjeta para activar los avisos al móvil.
import { useNavigate } from 'react-router';
import ActivarAvisos from '../comun/ActivarAvisos';
import { Icono } from '../comun/iconos';
import { useSesion } from '../comun/sesion';
import { fraseInicio } from '../comun/modificaciones';
import { useBandeja } from '../comun/useBandeja';
import RejillaModelos from './RejillaModelos';

export default function Inicio() {
  const navegar = useNavigate();
  const { sesion } = useSesion();
  // Las órdenes de despacho sin abrir, de la consulta de la bandeja que lleva el marco.
  const { resumen } = useBandeja();
  const nuevas = resumen?.nuevas ?? 0;

  return (
    <>
      <h2 className="saludo">Hola, {sesion?.nombre}</h2>
      <p className="sub">{fraseInicio(resumen)}</p>
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
