package com.puntotres.packinglist.service.corte;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * El camino rápido de los HEIC en Windows: el decodificador del sistema
 * (WIC) a través de PowerShell, un proceso por lote.
 *
 * Medido con las mismas fotos que el camino Java: ~1,7 s una foto de 12 MP y
 * ~4 s una de 24 MP, con la memoria fuera de la JVM. Necesita las
 * extensiones HEIF y HEVC de Windows; si no están, cada foto falla deprisa y
 * vuelve como pendiente para el camino Java, así que no hace falta
 * comprobarlas antes.
 */
public class ConversorHeicWindows {

    /** Una foto que convertir y dónde dejar su JPEG. */
    public record Trabajo(Path entrada, Path salida) {
    }

    private static final String SCRIPT = "/corte/heic-a-jpeg.ps1";

    /**
     * Tiempo que se le da a un lote: un minuto de arranque y otro por foto,
     * quince veces lo medido con las de 24 MP. Pasado, el proceso se mata y
     * sus fotos vuelven como pendientes para el camino Java: un códec que se
     * cuelga con un fichero raro dejaría el lote esperando para siempre,
     * porque la salida solo se acaba cuando PowerShell sale.
     */
    private static final Duration ARRANQUE = Duration.ofMinutes(1);
    private static final Duration POR_FOTO = Duration.ofMinutes(1);

    private final String script;
    private final Duration arranque;
    private final Duration porFoto;

    public ConversorHeicWindows() {
        this(SCRIPT, ARRANQUE, POR_FOTO);
    }

    ConversorHeicWindows(String script, Duration arranque, Duration porFoto) {
        this.script = script;
        this.arranque = arranque;
        this.porFoto = porFoto;
    }

    public static boolean disponible() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * Convierte el lote en un proceso de PowerShell y avisa de cada foto
     * según sale, para que el progreso se vea. Devuelve las que NO han salido
     * (Windows no las lee, o el proceso se ha cortado); lanza IOException
     * solo si PowerShell ni siquiera ha arrancado.
     */
    public List<Trabajo> convertir(List<Trabajo> trabajos, Path carpeta,
                                   Consumer<Process> registrar, Consumer<Trabajo> alConvertir)
            throws IOException, InterruptedException {
        if (trabajos.isEmpty()) {
            return List.of();
        }
        Path script = Files.createTempFile(carpeta, "heic-a-jpeg-", ".ps1");
        Path lista = Files.createTempFile(carpeta, "heic-lista-", ".txt");
        try {
            try (InputStream contenido = ConversorHeicWindows.class.getResourceAsStream(this.script)) {
                if (contenido == null) {
                    throw new IOException("falta el script " + this.script);
                }
                Files.copy(contenido, script, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(lista, trabajos.stream()
                    .map(trabajo -> trabajo.entrada().toAbsolutePath() + "\t"
                            + trabajo.salida().toAbsolutePath())
                    .toList(), StandardCharsets.UTF_8);

            Process proceso = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toAbsolutePath().toString(),
                    "-lista", lista.toAbsolutePath().toString(),
                    "-max", String.valueOf(ReductorImagen.LADO_MAXIMO))
                    .redirectErrorStream(true)
                    .start();
            registrar.accept(proceso);
            Duration limite = arranque.plus(porFoto.multipliedBy(trabajos.size()));
            CompletableFuture<Void> vigilante = CompletableFuture.runAsync(() -> matar(proceso),
                    CompletableFuture.delayedExecutor(limite.toMillis(), TimeUnit.MILLISECONDS));

            Set<Integer> convertidas = new HashSet<>();
            try (BufferedReader lector = new BufferedReader(
                    new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    int indice = indiceConvertido(linea);
                    if (indice >= 0 && indice < trabajos.size()
                            && Files.isRegularFile(trabajos.get(indice).salida())
                            && convertidas.add(indice)) {
                        alConvertir.accept(trabajos.get(indice));
                    }
                }
            } catch (IOException e) {
                // Salida cortada (proceso cancelado o muerto): lo no confirmado vuelve como pendiente.
            } finally {
                vigilante.cancel(false);
            }
            // La salida ya se ha cerrado, así que el proceso está saliendo o muerto.
            if (!proceso.waitFor(1, TimeUnit.MINUTES)) {
                matar(proceso);
            }

            List<Trabajo> pendientes = new ArrayList<>();
            for (int i = 0; i < trabajos.size(); i++) {
                if (!convertidas.contains(i)) {
                    pendientes.add(trabajos.get(i));
                }
            }
            return pendientes;
        } finally {
            Files.deleteIfExists(lista);
            Files.deleteIfExists(script);
        }
    }

    /** Con sus hijos: en Windows matar al padre no mata a los hijos. */
    private static void matar(Process proceso) {
        proceso.descendants().forEach(ProcessHandle::destroyForcibly);
        proceso.destroyForcibly();
    }

    /** "OK<TAB>n" → n-1; cualquier otra línea → -1. */
    private static int indiceConvertido(String linea) {
        String[] partes = linea.split("\t", 3);
        if (partes.length < 2 || !"OK".equals(partes[0].trim())) {
            return -1;
        }
        try {
            return Integer.parseInt(partes[1].trim()) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
