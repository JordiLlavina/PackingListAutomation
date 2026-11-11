// Recorrido de humo de la app de modelos (spec §11): despacho da de alta un
// modelo y un usuario del taller por la API; la tablet del taller entra con
// el código de alta, avisa de un cambio con una foto (de fichero: sin cámara
// ni micrófono en Chromium sin ventana) y despacho lo ve en su bandeja.
import { expect, test, type APIResponse, type BrowserContext } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const FOTO = path.join(path.dirname(fileURLToPath(import.meta.url)), 'foto.png');

// Un sufijo por ejecución: así el recorrido se puede repetir contra una
// aplicación que ya tenga los datos de una vuelta anterior (npm run e2e a mano).
const SUFIJO = String(Date.now()).slice(-6);
const PROTOTIPO = `PROTO E2E ${SUFIJO}`;

/** El token CSRF de la cookie XSRF-TOKEN, que la API pide en la cabecera de cada cambio. */
async function tokenCsrf(contexto: BrowserContext): Promise<string> {
  // Cualquier petición a la API deja la cookie puesta.
  await contexto.request.get('/api/sesion');
  const cookie = (await contexto.cookies()).find((c) => c.name === 'XSRF-TOKEN');
  expect(cookie, 'la API no ha dejado la cookie XSRF-TOKEN').toBeDefined();
  return cookie!.value;
}

async function cuerpo<T>(respuesta: APIResponse): Promise<T> {
  expect(respuesta.ok(), `${respuesta.url()} → ${respuesta.status()}: ${await respuesta.text()}`).toBeTruthy();
  return (await respuesta.json()) as T;
}

test('Bachir avisa de un cambio con una foto y despacho lo ve en la bandeja', async ({ browser }) => {
  // a) Despacho (el admin) prepara el terreno por la API.
  const despacho = await browser.newContext();
  await cuerpo(await despacho.request.post('/api/sesion', { data: { login: 'admin', password: 'prueba' } }));
  const cabeceras = { 'X-XSRF-TOKEN': await tokenCsrf(despacho) };

  const temporada = await cuerpo<{ id: number }>(
    await despacho.request.post('/api/temporadas', { headers: cabeceras, data: { cliente: 'AMI', temporada: `E2E ${SUFIJO}` } }),
  );
  await cuerpo(
    await despacho.request.post('/api/modelos', {
      headers: cabeceras,
      data: { cliente: 'AMI', nombrePrototipo: PROTOTIPO, nombre: 'BOLSO E2E', temporadaId: temporada.id },
    }),
  );
  const bachir = await cuerpo<{ id: number }>(
    await despacho.request.post('/api/usuarios', {
      headers: cabeceras,
      data: { nombre: 'Bachir', login: `bachir${SUFIJO}`, password: 'prueba', rol: 'TALLER' },
    }),
  );
  const { codigo } = await cuerpo<{ codigo: string }>(
    await despacho.request.post(`/api/usuarios/${bachir.id}/codigo-alta`, { headers: cabeceras }),
  );
  expect(codigo).toMatch(/^\d{6}$/);

  // b) La tablet del taller, sin cookies ni permisos de cámara o micrófono.
  const taller = await browser.newContext({ permissions: [] });
  const tablet = await taller.newPage();
  await tablet.goto('/app/');
  await tablet.getByRole('button', { name: 'Soy del taller' }).click();
  for (const cifra of codigo) {
    await tablet.getByRole('button', { name: cifra, exact: true }).click();
  }
  await expect(tablet.getByText(codigo)).toBeVisible();
  await tablet.getByRole('button', { name: 'OK', exact: true }).click();
  await expect(tablet.getByText('Hola, Bachir')).toBeVisible();

  await tablet.getByRole('button', { name: /Crear Modificación/ }).click();
  await tablet.getByRole('button', { name: new RegExp(PROTOTIPO) }).click();
  await tablet.getByRole('button', { name: 'Fornituras' }).click();
  await tablet.getByRole('button', { name: 'Siguiente', exact: true }).click();

  await tablet.locator('input[type=file]').setInputFiles(FOTO);
  const siguienteConFoto = tablet.getByRole('button', { name: 'Siguiente (1)' });
  await expect(siguienteConFoto).toBeEnabled();
  await siguienteConFoto.click();

  // Sin micrófono se sigue solo con la foto.
  await tablet.getByRole('button', { name: 'Siguiente', exact: true }).click();
  await tablet.getByRole('button', { name: 'Enviar a despacho' }).click();
  await expect(tablet.getByText('Enviado a despacho')).toBeVisible();

  // c) Despacho abre su bandeja: el aviso está en «Del taller», como nuevo.
  const bandeja = await despacho.newPage();
  await bandeja.goto('/app/bandeja');
  await expect(bandeja.getByRole('button', { name: 'Del taller' })).toHaveAttribute('aria-pressed', 'true');
  const aviso = bandeja.getByRole('listitem').filter({ hasText: PROTOTIPO });
  await expect(aviso).toHaveCount(1);
  await expect(aviso.getByText('Nueva', { exact: true })).toBeVisible();

  await taller.close();
  await despacho.close();
});
