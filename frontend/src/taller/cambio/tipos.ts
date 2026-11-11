// Los tipos de cambio (piel, forro, herrajes…) los da el servidor, que es
// donde se configuran. Se piden una vez: el paso de elegir y el de revisar
// usan la misma lista.
import { useCallback, useEffect, useState } from 'react';
import { ErrorApi, api } from '../../comun/api';
import { esNombreIcono, type NombreIcono } from '../../comun/iconos';

export type TipoCambio = { clave: string; texto: string; icono: NombreIcono };

let pedidos: Promise<TipoCambio[]> | null = null;

function pedir(): Promise<TipoCambio[]> {
  pedidos ??= api<{ clave: string; texto: string; icono: string }[]>('/api/tipos-cambio')
    // Un icono que no tenemos (alguien lo ha escrito mal en la configuración) sale como «otro».
    .then((lista) => lista.map((t) => ({ ...t, icono: esNombreIcono(t.icono) ? t.icono : 'otro' })))
    .catch((e: unknown) => {
      pedidos = null;
      throw e;
    });
  return pedidos;
}

export function useTiposCambio(): { tipos: TipoCambio[] | null; error: string | null; reintentar: () => void } {
  const [tipos, setTipos] = useState<TipoCambio[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const cargar = useCallback(() => {
    setError(null);
    pedir()
      .then(setTipos)
      .catch((e: unknown) => setError(e instanceof ErrorApi ? e.message : 'No se han podido cargar los tipos de cambio'));
  }, []);
  useEffect(cargar, [cargar]);
  return { tipos, error, reintentar: cargar };
}
