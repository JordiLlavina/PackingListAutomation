// El último paso de `mvn verify -Pe2e`, en la fase verify: dice si el
// recorrido de humo ha ido bien leyendo el informe JSON de Playwright.
//
// Existe para que un fallo no deje la aplicación arrancada: si `npm run e2e`
// parase el build en integration-test, Maven no llegaría a post-integration-
// test, donde se para el jar, y en Windows el java hijo se queda con el puerto
// 8089 (y con la salida de la consola abierta). Así que el recorrido corre sin
// parar el build, se para la aplicación y aquí se decide. Es lo que hace
// maven-failsafe-plugin con los tests de Java.
import { readFileSync } from 'node:fs';

const INFORME = 'playwright-report/resultado.json';

function fallar(motivo) {
  console.error(`Recorrido de humo: ${motivo}`);
  process.exit(1);
}

// -DskipTests se salta el recorrido (lo hace frontend-maven-plugin en
// integration-test), así que tampoco hay nada que comprobar.
if (process.env.E2E_SIN_TESTS === 'true') {
  console.log('Recorrido de humo saltado (-DskipTests).');
  process.exit(0);
}

let informe;
try {
  informe = JSON.parse(readFileSync(INFORME, 'utf8'));
} catch {
  fallar(`no hay informe en frontend/${INFORME}: Playwright no ha llegado a correr.`);
}

const { stats } = informe;
// Un informe de una vuelta anterior no vale: el de esta tiene que haber
// empezado después de arrancar el build.
const inicioBuild = Date.parse(process.env.E2E_INICIO_BUILD ?? '');
if (!Number.isNaN(inicioBuild) && Date.parse(stats.startTime) < inicioBuild) {
  fallar(`el informe es de una vuelta anterior (${stats.startTime}): Playwright no ha llegado a correr.`);
}
if (stats.unexpected > 0) {
  fallar(`${stats.unexpected} prueba(s) fallida(s). Detalle y traza en frontend/test-results/.`);
}
if (stats.expected === 0) {
  fallar('no ha corrido ninguna prueba.');
}
console.log(`Recorrido de humo: ${stats.expected} prueba(s) bien.`);
