package com.puntotres.packinglist.shipping.importacion;

import static com.puntotres.packinglist.shipping.importacion.LectorCsv.columna;

import java.util.*;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.model.BillingAccount;
import com.puntotres.packinglist.shipping.model.BillingRole;
import com.puntotres.packinglist.shipping.model.ContactType;
import com.puntotres.packinglist.shipping.model.Incoterm;
import com.puntotres.packinglist.shipping.service.BillingAccountService;
import com.puntotres.packinglist.shipping.service.ContactService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;
import com.puntotres.packinglist.shipping.service.DatosContacto;

/**
 * Importa el export CSV de la libreta de direcciones de MyDHL+ (44 columnas,
 * cabeceras en español, UTF-8 con dos BOM). El mapeo de columnas está fijado
 * con el export real de septiembre de 2026; ver la spec, sección 9.
 *
 * Las columnas "Por Defecto ..." traen números de cuenta DHL de terceros:
 * se dan de alta como BillingAccount y quedan como cuentas por defecto del
 * contacto, para que al elegirlo en un envío ya salga la cuenta que paga.
 */
@Service
public class ImportadorLibretaMyDhl {

    static final String NOMBRE = "Nombre y apellidos", EMPRESA = "Empresa", ALIAS = "Alias", ALIAS2 = "Alias 2",
            EMAIL1 = "Email 1", EMAIL2 = "Email 2", PREFIJO = "Número de teléfono Código de país", TELEFONO = "Número de teléfono",
            EXTENSION = "Extensión", NIF = "NIF/CIF", EORI = "Número EORI", PAIS = "Código de país",
            DIR1 = "Dirección 1", DIR2 = "Dirección 2", DIR3 = "Dirección 3", CP = "CP Código Postal", CIUDAD = "Ciudad",
            PROVINCIA = "Estado / Provincia", PROVINCIA_COD = "Estado / Provincia Código",
            CTA_REMITENTE = "Por Defecto Remitente Cuenta", CTA_CARGOS = "Por Defecto Enviar Cargos (Fracturar a)",
            CTA_ARANCELES_IMP = "Por Defecto Arancel/Impuestos (Fracturar a)", CTA_ARANCELES = "Por Defecto Arancel (Fracturar a)",
            CTA_IMPUESTOS = "Por Defecto Impuesto (Fracturar a)", INCOTERM = "Términos aduaneros", NOTAS = "Notas",
            ROL = "Parte adicional Rol", RESIDENCIAL = "Entregas residenciales";

    private final ContactService contactos;
    private final BillingAccountService cuentas;

    public ImportadorLibretaMyDhl(ContactService contactos, BillingAccountService cuentas) {
        this.contactos = contactos;
        this.cuentas = cuentas;
    }

    public PrevisualizacionLibreta previsualizar(byte[] csv, String nombreFichero) {
        List<CSVRecord> registros = LectorCsv.leer(csv);
        if (registros.isEmpty() || !registros.get(0).isMapped(EMPRESA) || !registros.get(0).isMapped(PAIS)) {
            throw new DatoInvalidoException("Esto no parece la libreta de MyDHL+: faltan las columnas '" + EMPRESA + "' y '" + PAIS + "'.");
        }
        List<FilaLibreta> filas = new ArrayList<>();
        Map<String, Integer> vistas = new HashMap<>();
        int numero = 0;
        for (CSVRecord r : registros) {
            numero++;
            DatosContacto datos = datos(r);
            List<String> errores = errores(datos);
            String duplicadoDe = null;
            if (errores.isEmpty()) {
                String clave = (datos.empresa() + "|" + (datos.persona() == null ? "" : datos.persona())).toUpperCase(Locale.ROOT);
                Integer anterior = vistas.putIfAbsent(clave, numero);
                if (anterior != null) duplicadoDe = "fila " + anterior;
                else if (contactos.existe(datos.empresa(), datos.persona())) duplicadoDe = "ya en la libreta";
            }
            filas.add(new FilaLibreta(numero, datos, columna(r, CTA_REMITENTE), columna(r, CTA_CARGOS),
                    primero(columna(r, CTA_ARANCELES_IMP), columna(r, CTA_ARANCELES), columna(r, CTA_IMPUESTOS)),
                    errores, duplicadoDe));
        }
        return new PrevisualizacionLibreta(nombreFichero, filas);
    }

    @Transactional
    public ResultadoImportacion importar(PrevisualizacionLibreta prev, Set<Integer> duplicadasAImportar) {
        int altas = 0, omitidas = 0;
        List<String> errores = new ArrayList<>();
        for (FilaLibreta fila : prev.filas()) {
            if (!fila.valida()) {
                errores.add("Fila " + fila.numero() + ": " + String.join(" ", fila.errores()));
                continue;
            }
            if (fila.duplicada() && !duplicadasAImportar.contains(fila.numero())) {
                omitidas++;
                continue;
            }
            try {
                DatosContacto d = fila.datos();
                Long transporte = cuenta(fila.cuentaTransporte(), d.empresa(), BillingRole.PAYER,
                        cuentaSi(fila.cuentaRemitente(), fila.cuentaTransporte(), BillingRole.SHIPPER),
                        cuentaSi(fila.cuentaAranceles(), fila.cuentaTransporte(), BillingRole.DUTIES));
                Long aranceles = fila.cuentaAranceles() == null || fila.cuentaAranceles().equals(fila.cuentaTransporte()) ? transporte
                        : cuenta(fila.cuentaAranceles(), d.empresa(), BillingRole.DUTIES,
                        cuentaSi(fila.cuentaRemitente(), fila.cuentaAranceles(), BillingRole.SHIPPER));
                if (fila.cuentaRemitente() != null && !fila.cuentaRemitente().equals(fila.cuentaTransporte())
                        && !fila.cuentaRemitente().equals(fila.cuentaAranceles())) {
                    cuenta(fila.cuentaRemitente(), d.empresa(), BillingRole.SHIPPER);
                }
                if (fila.cuentaAranceles() == null) aranceles = null;
                contactos.guardar(null, new DatosContacto(d.empresa(), d.persona(), d.direccion1(), d.direccion2(), d.direccion3(),
                        d.codigoPostal(), d.ciudad(), d.provincia(), d.provinciaCodigo(), d.pais(), d.telefono(), d.email(),
                        d.tipo(), d.residencial(), d.nifVat(), d.eori(), d.identificadorMarroqui(), d.notas(),
                        d.incotermPorDefecto(), transporte, aranceles, d.alias()));
                altas++;
            } catch (DatoInvalidoException e) {
                errores.add("Fila " + fila.numero() + ": " + e.getMessage());
            }
        }
        return new ResultadoImportacion(altas, 0, omitidas, errores);
    }

    /** Alta o roles añadidos de una cuenta; null si no hay número. Devuelve el id. */
    private Long cuenta(String numero, String titular, BillingRole rol, BillingRole... extra) {
        if (numero == null) return null;
        EnumSet<BillingRole> roles = EnumSet.of(rol);
        for (BillingRole r : extra) if (r != null) roles.add(r);
        BillingAccount c = cuentas.asegurar(numero, titular, false, roles);
        return c.getId();
    }

    private static BillingRole cuentaSi(String candidata, String misma, BillingRole rol) {
        return candidata != null && candidata.equals(misma) ? rol : null;
    }

    private static DatosContacto datos(CSVRecord r) {
        String persona = columna(r, NOMBRE);
        String empresa = columna(r, EMPRESA);
        if (empresa == null) empresa = persona;
        List<String> alias = new ArrayList<>();
        for (String a : new String[] {columna(r, ALIAS), columna(r, ALIAS2)}) {
            String limpio = aliasUtil(a, persona, empresa);
            if (limpio != null) alias.add(limpio);
        }
        StringBuilder notas = new StringBuilder();
        if (columna(r, NOTAS) != null) notas.append(columna(r, NOTAS));
        if (columna(r, EMAIL2) != null) notas.append(notas.isEmpty() ? "" : ". ").append("Otro email: ").append(columna(r, EMAIL2));
        if (columna(r, EXTENSION) != null) notas.append(notas.isEmpty() ? "" : ". ").append("Tel. ext. ").append(columna(r, EXTENSION));
        Incoterm incoterm = null;
        try { if (columna(r, INCOTERM) != null) incoterm = Incoterm.valueOf(columna(r, INCOTERM).toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignorado) { }
        return new DatosContacto(empresa, persona, columna(r, DIR1), columna(r, DIR2), columna(r, DIR3), columna(r, CP), columna(r, CIUDAD),
                columna(r, PROVINCIA), columna(r, PROVINCIA_COD), columna(r, PAIS) == null ? null : columna(r, PAIS).toUpperCase(Locale.ROOT),
                telefono(columna(r, PREFIJO), columna(r, TELEFONO)), columna(r, EMAIL1),
                "PR".equalsIgnoreCase(columna(r, ROL)) ? ContactType.PRIVATE : ContactType.BUSINESS,
                "Y".equalsIgnoreCase(columna(r, RESIDENCIAL)),
                columna(r, NIF), columna(r, EORI), null, notas.isEmpty() ? null : notas.toString(), incoterm, null, null, alias);
    }

    /** +prefijo y número sin espacios ni el cero nacional inicial (0612345678 → +33612345678). */
    static String telefono(String prefijo, String numero) {
        if (numero == null) return null;
        String digitos = numero.replaceAll("[^0-9]", "");
        if (prefijo == null || prefijo.isBlank()) return digitos;
        digitos = digitos.replaceFirst("^0+", "");
        return "+" + prefijo.replaceAll("[^0-9]", "") + digitos;
    }

    /** MyDHL+ compone "Persona at Empresa" y deja restos como "X at " o " at Y"; se descarta lo que no añade nada. */
    static String aliasUtil(String alias, String persona, String empresa) {
        if (alias == null) return null;
        String limpio = alias.trim().replaceFirst("(?i)\\s+at$", "").replaceFirst("(?i)^at\\s+", "").trim();
        if (limpio.isEmpty() || limpio.equalsIgnoreCase("at")) return null;
        if (limpio.equalsIgnoreCase(persona) || limpio.equalsIgnoreCase(empresa)) return null;
        return limpio;
    }

    private static List<String> errores(DatosContacto d) {
        List<String> errores = new ArrayList<>();
        if (d.empresa() == null && d.persona() == null) errores.add("Sin empresa ni persona.");
        if (d.pais() == null || d.pais().length() != 2) errores.add("Sin código de país.");
        if (d.direccion1() == null) errores.add("Sin dirección.");
        if (d.ciudad() == null) errores.add("Sin ciudad.");
        return errores;
    }

    private static String primero(String... valores) {
        for (String v : valores) if (v != null) return v;
        return null;
    }
}
