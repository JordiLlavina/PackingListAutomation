package com.puntotres.packinglist.service.embalaje;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSettings;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;

/**
 * Mete la fuente <b>Gentium Basic</b> dentro del {@code .docx} de la hoja de
 * embalaje, en normal y en negrita, y la deja como fuente por defecto del
 * documento.
 *
 * Hace falta incrustarla porque no se puede contar con que el PC que abra la
 * hoja la tenga instalada, y Word <b>no avisa</b> de una fuente que falta: la sustituye por
 * otra parecida en silencio y la hoja se imprime con otra letra, otros anchos
 * y, a veces, en dos páginas. Con la fuente dentro del fichero Word la usa
 * aunque no esté en el sistema. La licencia (SIL Open Font License, en
 * {@code fonts/gentium-basic/OFL.txt}) permite incrustarla en documentos.
 *
 * Solo se incrustan la normal y la negrita, que son las únicas que usa la
 * hoja (no lleva cursiva), y en {@code resources} solo están esas dos más la
 * licencia: cada TTF son ~260 KB que viajan en cada {@code .docx}.
 *
 * Cómo se incrusta, según ECMA-376 (parte 1, §17.8.3, y parte 2 para la
 * ofuscación): una parte {@code word/fontTable.xml} enlazada desde el
 * documento, con una entrada {@code <w:font>} que apunta a cada fichero
 * ({@code <w:embedRegular>}, {@code <w:embedBold>}) con su clave; cada
 * fichero es una parte {@code word/fonts/fontN.odttf} <b>ofuscada</b> (los 32
 * primeros bytes del TTF, XOR con la clave); y {@code <w:embedTrueTypeFonts/>}
 * en los ajustes. POI no sabe hacer nada de esto, así que las partes se
 * crean a mano en el paquete OPC.
 */
final class FuenteIncrustada {

    /** Nombre de familia de la fuente, el que lleva dentro el TTF y el que se escribe en cada run. */
    static final String NOMBRE = "Gentium Basic";

    /** Tipo de contenido de una fuente ofuscada dentro de un paquete OOXML. */
    static final String TIPO_FUENTE_OFUSCADA = "application/vnd.openxmlformats-officedocument.obfuscatedFont";

    private static final String TIPO_TABLA_FUENTES =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml";
    private static final String RELACION_TABLA_FUENTES =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable";
    private static final String RELACION_FUENTE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/font";
    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final String RECURSO_NORMAL = "/fonts/gentium-basic/GenBasR.ttf";
    private static final String RECURSO_NEGRITA = "/fonts/gentium-basic/GenBasB.ttf";

    /**
     * Claves de ofuscación, una por fichero. Son GUID cualesquiera; se fijan en
     * vez de sortearlas para que dos hojas iguales den el mismo {@code .docx}.
     */
    private static final String CLAVE_NORMAL = "{6A1D3C52-0E4B-4F7A-9C21-58B3D7E40F11}";
    private static final String CLAVE_NEGRITA = "{B47E9F20-3C5D-4A18-8E66-21F0C9A7D532}";

    /** Bytes del principio del TTF que se ofuscan, y largo de la clave. */
    private static final int BYTES_OFUSCADOS = 32;
    private static final int BYTES_CLAVE = 16;

    private static final byte[] TTF_NORMAL = leer(RECURSO_NORMAL);
    private static final byte[] TTF_NEGRITA = leer(RECURSO_NEGRITA);

    private FuenteIncrustada() {
    }

    /**
     * Deja la fuente como la de por defecto del documento, la incrusta y
     * marca en los ajustes que el documento lleva fuentes dentro. Se llama una
     * vez, con el documento ya escrito y antes de guardarlo.
     */
    static void aplicar(XWPFDocument doc) throws IOException {
        fuentePorDefecto(doc);
        CTSettings ajustes = doc.getSettings().getCTSettings();
        if (!ajustes.isSetEmbedTrueTypeFonts()) {
            ajustes.addNewEmbedTrueTypeFonts();
        }
        incrustar(doc);
    }

    /**
     * Ofusca (o desofusca, es la misma operación) los 32 primeros bytes de una
     * fuente con su clave: XOR con los 16 bytes del GUID leídos en orden
     * inverso, que es lo que pide ECMA-376 parte 2. Devuelve una copia.
     */
    static byte[] ofuscar(byte[] fuente, String guid) {
        byte[] clave = clave(guid);
        byte[] salida = fuente.clone();
        for (int i = 0; i < Math.min(BYTES_OFUSCADOS, salida.length); i++) {
            salida[i] ^= clave[i % BYTES_CLAVE];
        }
        return salida;
    }

    /**
     * Los 16 bytes del GUID en orden inverso: el texto sin llaves ni guiones,
     * leído de dos en dos cifras, y el último par es el primer byte de la clave.
     */
    private static byte[] clave(String guid) {
        String hex = guid.replaceAll("[{}-]", "");
        byte[] clave = new byte[BYTES_CLAVE];
        for (int i = 0; i < BYTES_CLAVE; i++) {
            clave[BYTES_CLAVE - 1 - i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return clave;
    }

    /**
     * La fuente por defecto en los estilos del documento, para todos los
     * rangos de caracteres: lo que no fije su fuente sale igual en Gentium.
     */
    private static void fuentePorDefecto(XWPFDocument doc) {
        CTStyles estilos = CTStyles.Factory.newInstance();
        CTFonts fuentes = estilos.addNewDocDefaults().addNewRPrDefault().addNewRPr().addNewRFonts();
        fuentes.setAscii(NOMBRE);
        fuentes.setHAnsi(NOMBRE);
        fuentes.setCs(NOMBRE);
        fuentes.setEastAsia(NOMBRE);
        doc.createStyles().setStyles(estilos);
    }

    /** Crea {@code word/fontTable.xml} y las dos fuentes ofuscadas, con sus relaciones. */
    private static void incrustar(XWPFDocument doc) throws IOException {
        try {
            OPCPackage paquete = doc.getPackage();
            PackagePartName nombreTabla = PackagingURIHelper.createPartName("/word/fontTable.xml");
            PackagePart tabla = paquete.createPart(nombreTabla, TIPO_TABLA_FUENTES);
            doc.getPackagePart().addRelationship(nombreTabla, TargetMode.INTERNAL, RELACION_TABLA_FUENTES);

            String idNormal = parteFuente(paquete, tabla, 1, ofuscar(TTF_NORMAL, CLAVE_NORMAL));
            String idNegrita = parteFuente(paquete, tabla, 2, ofuscar(TTF_NEGRITA, CLAVE_NEGRITA));

            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<w:fonts xmlns:w=\"" + NS_W + "\" xmlns:r=\"" + NS_R + "\">"
                    + "<w:font w:name=\"" + NOMBRE + "\">"
                    + "<w:charset w:val=\"00\"/><w:family w:val=\"roman\"/><w:pitch w:val=\"variable\"/>"
                    + "<w:embedRegular r:id=\"" + idNormal + "\" w:fontKey=\"" + CLAVE_NORMAL + "\"/>"
                    + "<w:embedBold r:id=\"" + idNegrita + "\" w:fontKey=\"" + CLAVE_NEGRITA + "\"/>"
                    + "</w:font></w:fonts>";
            try (OutputStream salida = tabla.getOutputStream()) {
                salida.write(xml.getBytes(StandardCharsets.UTF_8));
            }
        } catch (InvalidFormatException e) {
            throw new IOException("no se ha podido incrustar la fuente " + NOMBRE + " en el Word", e);
        }
    }

    /** Una fuente ofuscada como parte {@code word/fonts/fontN.odttf}; devuelve el id de su relación. */
    private static String parteFuente(OPCPackage paquete, PackagePart tabla, int numero, byte[] ofuscada)
            throws InvalidFormatException, IOException {
        PackagePartName nombre = PackagingURIHelper.createPartName("/word/fonts/font" + numero + ".odttf");
        PackagePart parte = paquete.createPart(nombre, TIPO_FUENTE_OFUSCADA);
        try (OutputStream salida = parte.getOutputStream()) {
            salida.write(ofuscada);
        }
        return tabla.addRelationship(nombre, TargetMode.INTERNAL, RELACION_FUENTE).getId();
    }

    private static byte[] leer(String recurso) {
        try (InputStream entrada = FuenteIncrustada.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException("falta la fuente " + recurso + " en el classpath");
            }
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("no se ha podido leer la fuente " + recurso, e);
        }
    }
}
