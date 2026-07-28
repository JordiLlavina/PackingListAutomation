package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.etiquetas.CodigoBarrasEan13;

/**
 * Etiquetas de artículo de AMI: las que se enganchan al bolso o al cinturón,
 * con el código de barras EAN13 del pedido.
 *
 * A partir del excel de pedido de la temporada (ej. "EAN PUNTOTRES H26.xlsx")
 * genera un excel por grupo (tipo × país de fabricación), con una hoja por
 * fila del pedido —es decir por EAN13— y 40 etiquetas idénticas en cada hoja.
 *
 * Los cinturones son las referencias que empiezan por UBL; el resto son
 * bolsos. El país sale de la columna "Made in", que es la que hace que los
 * bolsos vayan en dos ficheros (MOROCCO y SPAIN).
 */
@Service
public class AmiEtiquetasArticuloGenerador implements GeneradorEtiquetasArticuloCliente {

    /** Longitud máxima de un nombre de hoja en Excel. */
    private static final int MAX_NOMBRE_HOJA = 31;

    /** Caracteres que Excel no admite en un nombre de hoja. */
    private static final Pattern PROHIBIDOS_EN_HOJA = Pattern.compile("[/\\\\?*:\\[\\]]");

    /**
     * Todo descendente, como los ficheros del cliente: artículo, color, PO y
     * talla. La talla se compara NUMÉRICAMENTE (105 antes que 95, no al
     * revés como saldría en texto); las no numéricas ("U") van al final.
     */
    private static final Comparator<FilaEan> ORDEN_HOJAS =
            Comparator.comparing(FilaEan::article, Comparator.reverseOrder())
                    .thenComparing(FilaEan::coloris, Comparator.reverseOrder())
                    .thenComparing(FilaEan::poCompacto, Comparator.reverseOrder())
                    .thenComparing(AmiEtiquetasArticuloGenerador::tallaNumerica,
                            Comparator.reverseOrder());

    private final EtiquetasArticuloExcelBuilder builder;

    public AmiEtiquetasArticuloGenerador(EtiquetasArticuloExcelBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String claveCliente() {
        return "AMI";
    }

    @Override
    public String tituloCampoPedido() {
        return "Introducir excel del pedido de AMI";
    }

    @Override
    public ResultadoEtiquetasArticulo generar(byte[] excelPedido, String temporadaPorDefecto)
            throws IOException {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(excelPedido);
        ResultadoEtiquetasArticulo resultado = new ResultadoEtiquetasArticulo();
        resultado.getAvisos().addAll(catalogo.avisos());

        // El nombre de la hoja del excel manda sobre lo que haya escrito el
        // usuario: es el dato del propio pedido.
        String temporada = catalogo.temporada()
                .orElseGet(() -> temporadaPorDefecto == null ? "" : temporadaPorDefecto.trim());
        if (temporada.isBlank()) {
            resultado.getAvisos().add("No se ha podido deducir la temporada del excel ni se ha "
                    + "indicado ninguna: los nombres de fichero la omiten");
        }

        for (Map.Entry<Grupo, List<FilaEan>> entrada : agrupar(catalogo.filas()).entrySet()) {
            Grupo grupo = entrada.getKey();
            List<FilaEan> filas = new ArrayList<>(entrada.getValue());
            filas.sort(ORDEN_HOJAS);

            List<String> nombres = nombresDeHoja(filas, grupo.cinturon());
            List<HojaEtiquetas> hojas = new ArrayList<>();
            for (int i = 0; i < filas.size(); i++) {
                hojas.add(new HojaEtiquetas(nombres.get(i),
                        etiquetaDe(filas.get(i), resultado.getAvisos())));
            }
            resultado.getExcels().add(new ExcelEtiquetasArticulo(
                    descripcion(grupo, hojas.size()),
                    nombreFichero(grupo, temporada),
                    builder.generar(hojas)));
        }
        return resultado;
    }

    // --- pasos ---

    /** Los cinturones de AMI son las referencias que empiezan por UBL. */
    private static boolean esCinturon(FilaEan fila) {
        return fila.article().startsWith("UBL");
    }

    /** Un grupo = un fichero. Se conserva el orden de aparición en el excel. */
    private static Map<Grupo, List<FilaEan>> agrupar(List<FilaEan> filas) {
        Map<Grupo, List<FilaEan>> porGrupo = new LinkedHashMap<>();
        for (FilaEan fila : filas) {
            porGrupo.computeIfAbsent(new Grupo(esCinturon(fila), fila.madeIn()),
                    grupo -> new ArrayList<>()).add(fila);
        }
        return porGrupo;
    }

    /**
     * Nombre de hoja de cada fila del grupo: "{ARTICLE} {color} {PO}{sufijo}"
     * y, en cinturones, " {TALLA}".
     *
     * Se intenta con el libellé, que es lo legible al buscar la hoja para
     * imprimir ("BLACK", "TRUFFLE"), y se cae al código COLORIS cuando el
     * nombre no cabría en los 31 caracteres de Excel o cuando dos filas
     * distintas darían el mismo nombre (dos COLORIS con el mismo libellé).
     * Nunca se trunca a media palabra.
     */
    private static List<String> nombresDeHoja(List<FilaEan> filas, boolean cinturon) {
        List<String> conLibelle = filas.stream()
                .map(fila -> nombre(fila, cinturon, sanear(fila.libelle())))
                .toList();
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            String candidato = conLibelle.get(i);
            boolean cabe = candidato.length() <= MAX_NOMBRE_HOJA;
            // O(n²) sobre 79 filas como máximo: irrelevante y más claro que
            // montar un mapa de frecuencias.
            boolean unico = Collections.frequency(conLibelle, candidato) == 1;
            nombres.add(recortar(cabe && unico
                    ? candidato
                    : nombre(filas.get(i), cinturon, sanear(filas.get(i).coloris()))));
        }
        return nombres;
    }

    private static String nombre(FilaEan fila, boolean cinturon, String color) {
        String base = fila.article() + " " + color + " " + fila.poCompacto();
        String completo = cinturon ? base + " " + fila.taille() : base;
        // Se sanea el nombre ENTERO, no solo el color: ARTICLE y PO también
        // acaban aquí, y un carácter prohibido en cualquiera de ellos haría
        // que POI rechazara la hoja y se cayera el grupo completo. El trim
        // final quita el espacio que deja un cinturón sin talla.
        return sanear(completo).trim();
    }

    private static String sanear(String texto) {
        return PROHIBIDOS_EN_HOJA.matcher(texto.trim()).replaceAll("-");
    }

    /**
     * Última red por si un artículo fuera más largo de lo habitual: con
     * COLORIS el nombre mide 30 como máximo, pero recortar es más barato que
     * dejar que POI lance al crear la hoja.
     */
    private static String recortar(String nombre) {
        return nombre.length() <= MAX_NOMBRE_HOJA
                ? nombre
                : nombre.substring(0, MAX_NOMBRE_HOJA);
    }

    /** La talla como número para ordenar; -1 si no es numérica ("U"). */
    private static int tallaNumerica(FilaEan fila) {
        return fila.taille().matches("\\d+") ? Integer.parseInt(fila.taille()) : -1;
    }

    /**
     * Formatea la etiqueta. Un EAN13 que no sean 13 dígitos con dígito de
     * control correcto se deja en null: la hoja se genera igual sin código de
     * barras y se avisa, en vez de bloquear el fichero entero.
     */
    private static EtiquetaArticulo etiquetaDe(FilaEan fila, List<String> avisos) {
        String ean13 = CodigoBarrasEan13.esValido(fila.ean13()) ? fila.ean13().trim() : null;
        if (ean13 == null) {
            avisos.add("Sin código de barras: " + identifica(fila)
                    + (fila.ean13().isBlank()
                            ? " (no trae EAN13)"
                            : " (EAN13 inválido: '" + fila.ean13() + "')"));
        }
        if (fila.taille().isBlank()) {
            avisos.add("Sin talla: " + identifica(fila));
        }
        return new EtiquetaArticulo(fila.article(), "Size: " + fila.taille(),
                fila.colorCompleto(), "Cde: " + fila.poNumerico(), ean13);
    }

    /** Cómo se nombra una fila en los avisos, para que Jordi la localice. */
    private static String identifica(FilaEan fila) {
        return fila.article() + " " + fila.colorCompleto()
                + " talla " + (fila.taille().isBlank() ? "?" : fila.taille())
                + " PO " + fila.poNumerico();
    }

    /**
     * "AMI CODE BARRE H26 MOROCCO.xlsx" para bolsos y
     * "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx" para cinturones:
     * ITEMS delante e CINTURONES al final, como los ficheros del cliente.
     */
    private static String nombreFichero(Grupo grupo, String temporada) {
        List<String> partes = new ArrayList<>(List.of("AMI", "CODE", "BARRE"));
        if (grupo.cinturon()) {
            partes.add("ITEMS");
        }
        if (!temporada.isBlank()) {
            partes.add(temporada);
        }
        if (!grupo.madeIn().isBlank()) {
            partes.add(grupo.madeIn());
        }
        if (grupo.cinturon()) {
            partes.add("CINTURONES");
        }
        return String.join(" ", partes) + ".xlsx";
    }

    /** Lo que se lee en la pantalla de resultados. */
    private static String descripcion(Grupo grupo, int hojas) {
        return (grupo.cinturon() ? "Cinturones" : "Bolsos")
                + " · " + (grupo.madeIn().isBlank() ? "sin país" : grupo.madeIn())
                + " · " + hojas + (hojas == 1 ? " hoja" : " hojas");
    }

    /** Un fichero: tipo de artículo por país de fabricación. */
    private record Grupo(boolean cinturon, String madeIn) {
    }
}
