// Escanear la etiqueta de un patrón: la cámara trasera a pantalla y, al leer el
// QR, la ficha del modelo — o, si se llega desde «Crear Modificación»
// (?para=cambio), el modelo queda elegido en el borrador y se sigue al tipo.
// Detecta con el BarcodeDetector del navegador si lo hay; si no, con zxing, que
// solo se descarga entonces.
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import type { ModeloFicha } from '../comun/modelos';
import { idDeModelo } from '../comun/qr';
import { toast } from '../comun/toast';
import { useBorrador } from './cambio/Borrador';

/** Lo que usamos del BarcodeDetector, que TypeScript todavía no trae. */
type DetectorQr = { detect: (fuente: HTMLVideoElement) => Promise<{ rawValue: string }[]> };
type ConstructorDetector = new (opciones: { formats: string[] }) => DetectorQr;

const CADA_MS = 300;
/** El mismo texto leído de nuevo dentro de este plazo no se vuelve a procesar. */
const REPETIDO_MS = 3000;

export default function Escanear() {
  const navegar = useNavigate();
  const [params] = useSearchParams();
  const paraCambio = params.get('para') === 'cambio';
  const codigoDePrueba = params.get('codigo');
  const { elegirModelo } = useBorrador();
  const video = useRef<HTMLVideoElement>(null);
  const [sinCamara, setSinCamara] = useState(false);
  // Cierra la cámara y el lector, sea cual sea.
  const parar = useRef<() => void>(() => {});
  const ocupado = useRef(false);
  const ultimo = useRef<{ texto: string; hora: number } | null>(null);

  const procesar = useCallback(
    (texto: string) => {
      if (ocupado.current) return;
      const ahora = Date.now();
      if (ultimo.current && ultimo.current.texto === texto && ahora - ultimo.current.hora < REPETIDO_MS) return;
      ultimo.current = { texto, hora: ahora };
      const id = idDeModelo(texto);
      if (id === null) {
        toast('Este código no es de una etiqueta de patrón');
        return;
      }
      if (!paraCambio) {
        ocupado.current = true;
        parar.current();
        navegar(`/modelos/${id}`);
        return;
      }
      ocupado.current = true;
      api<ModeloFicha>(`/api/modelos/${id}`)
        .then((ficha) => {
          parar.current();
          elegirModelo(ficha.modelo);
          navegar('/cambio/tipo', { replace: true });
        })
        .catch((e: unknown) => {
          ocupado.current = false;
          toast(e instanceof ErrorApi ? e.message : 'No se ha podido cargar el modelo');
        });
    },
    [paraCambio, navegar, elegirModelo],
  );

  // Un código dado en la dirección (?codigo=): se trata como leído, una vez.
  // Sirve para probar el recorrido sin cámara.
  useEffect(() => {
    if (codigoDePrueba) procesar(codigoDePrueba);
  }, [codigoDePrueba, procesar]);

  useEffect(() => {
    let vigente = true;
    let cierre = () => {};
    parar.current = () => {
      vigente = false;
      cierre();
    };

    async function arrancar() {
      const pantalla = video.current;
      if (!pantalla) return;
      try {
        const Detector = (window as unknown as { BarcodeDetector?: ConstructorDetector }).BarcodeDetector;
        if (Detector) {
          const detector = new Detector({ formats: ['qr_code'] });
          const flujo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } } });
          const soltar = () => flujo.getTracks().forEach((pista) => pista.stop());
          if (!vigente) return soltar();
          pantalla.srcObject = flujo;
          await pantalla.play().catch(() => {});
          let leyendo = false;
          const reloj = setInterval(() => {
            if (leyendo || pantalla.readyState < 2) return;
            leyendo = true;
            detector
              .detect(pantalla)
              .then((codigos) => codigos[0] && procesar(codigos[0].rawValue))
              .catch(() => {})
              .finally(() => (leyendo = false));
          }, CADA_MS);
          cierre = () => {
            clearInterval(reloj);
            soltar();
            pantalla.srcObject = null;
          };
          if (!vigente) cierre();
        } else {
          const { BrowserQRCodeReader } = await import('@zxing/browser');
          if (!vigente) return;
          const controles = await new BrowserQRCodeReader().decodeFromVideoDevice(undefined, pantalla, (resultado) => {
            if (resultado) procesar(resultado.getText());
          });
          cierre = () => controles.stop();
          if (!vigente) cierre();
        }
      } catch {
        if (vigente) setSinCamara(true);
      }
    }
    void arrancar();
    return () => parar.current();
  }, [procesar]);

  return (
    <>
      <h2 className="pregunta">Apunta al QR de la etiqueta del patrón</h2>
      {sinCamara ? (
        <p className="ayuda" role="alert">No puedo usar la cámara.</p>
      ) : (
        <div className="escaner">
          <video ref={video} playsInline muted autoPlay aria-label="Vista de la cámara" />
          <div className="marco" />
        </div>
      )}
      <div className="acciones">
        <button className="boton ancho" type="button" onClick={() => navegar(paraCambio ? '/cambio' : '/modelos')}>
          No puedo escanear: elegir en la lista
        </button>
      </div>
    </>
  );
}
