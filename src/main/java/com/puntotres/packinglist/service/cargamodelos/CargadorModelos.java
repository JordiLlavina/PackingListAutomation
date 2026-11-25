package com.puntotres.packinglist.service.cargamodelos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.puntotres.packinglist.modelos.service.Documentos;
import com.puntotres.packinglist.modelos.service.Modelos;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColor;
import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.Piel;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.ColorEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.EstadoModelo;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.FichaEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.FotoEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.PielEnCarga;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoColor;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * «Cargar modelos al Sistema»: mete en el catálogo los modelos ACEPTADOS de
 * un lote, todos o ninguno (una sola transacción). Si uno falla no entra
 * ninguno y la excepción dice cuál y por qué; el lote sigue como estaba.
 *
 * Lo que se escribe en un modelo que ya existe sigue una sola regla, campo a
 * campo: se escribe si en el catálogo está VACÍO (rellenar un hueco) o si el
 * usuario lo ha TOCADO en el panel (pisar a propósito). Lo que venía del
 * origen y nadie ha tocado nunca pisa nada guardado. Y no se propaga a las
 * pieles hermanas: en el panel cada piel está a la vista.
 *
 * Un modelo NUEVO se da de alta con {@link Modelos#darDeAlta} ANTES de sus
 * artículos: deja el histórico de nombre y de etapa, y nace en la etapa del
 * panel. {@code CatalogoModelos.asegurarArticulo} solo, lo crearía en
 * Producción y sin histórico.
 */
@Service
public class CargadorModelos {

    private static final Logger LOG = LoggerFactory.getLogger(CargadorModelos.class);
    private static final String MOTIVO_ETAPA = "Carga de modelos";

    /** Lo que ha entrado, para el resumen de la pantalla. */
    public record ResumenCarga(int modelosNuevos, int modelosCompletados, int pieles, int colores, int fotos,
                               int fichas, List<String> avisos) {

        public ResumenCarga {
            avisos = List.copyOf(avisos);
        }
    }

    /** Un modelo del lote no se ha podido cargar: no ha entrado ninguno. */
    public static class CargaFallida extends RuntimeException {

        public CargaFallida(String modelo, String motivo) {
            super("No se ha cargado nada: " + modelo + " ha fallado (" + motivo + ")");
        }
    }

    private final ConstructorLote constructor;
    private final Modelos modelos;
    private final ModeloRepository repositorioModelos;
    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotosCatalogo;
    private final Documentos documentos;
    private final MemoriaPieles memoriaPieles;
    private final PielRepository pieles;
    private final TablaColores tablaColores;
    private final TransactionTemplate transaccion;

    public CargadorModelos(ConstructorLote constructor, Modelos modelos, ModeloRepository repositorioModelos,
                           CatalogoModelos catalogo, FotosCatalogo fotosCatalogo, Documentos documentos,
                           MemoriaPieles memoriaPieles, PielRepository pieles, TablaColores tablaColores,
                           TransactionTemplate transaccion) {
        this.constructor = constructor;
        this.modelos = modelos;
        this.repositorioModelos = repositorioModelos;
        this.catalogo = catalogo;
        this.fotosCatalogo = fotosCatalogo;
        this.documentos = documentos;
        this.memoriaPieles = memoriaPieles;
        this.pieles = pieles;
        this.tablaColores = tablaColores;
        this.transaccion = transaccion;
    }

    /**
     * Carga los aceptados y los quita del lote; los pendientes y los
     * cancelados se quedan, para seguir con ellos.
     *
     * @throws IllegalArgumentException si no hay nada que cargar o las fotos aún se están preparando
     * @throws CargaFallida             si un modelo falla: no entra ninguno
     */
    public ResumenCarga cargar(LoteCarga lote) {
        List<ModeloEnCarga> aceptados = lote.modelos.stream()
                .filter(modelo -> modelo.estado == EstadoModelo.ACEPTADO)
                .toList();
        if (aceptados.isEmpty()) {
            throw new IllegalArgumentException("No hay ningún modelo aceptado: acepta alguno antes de cargar");
        }
        if (!lote.fotosListas()) {
            throw new IllegalArgumentException("Las fotos todavía se están preparando: espera a que acaben");
        }
        for (ModeloEnCarga modelo : aceptados) {
            List<String> faltan = constructor.faltan(lote, modelo);
            if (!faltan.isEmpty()) {
                throw new CargaFallida(modelo.nombreVisible(), faltan.get(0));
            }
        }
        ResumenCarga resumen = transaccion.execute(estado -> cargarTodos(aceptados));
        // Solo después de confirmar: si la transacción falla, el lote sigue entero.
        lote.modelos.removeAll(aceptados);
        return resumen;
    }

    private ResumenCarga cargarTodos(List<ModeloEnCarga> aceptados) {
        Cuenta cuenta = new Cuenta();
        for (ModeloEnCarga modelo : aceptados) {
            try {
                cargar(modelo, cuenta);
            } catch (CargaFallida e) {
                throw e;
            } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException e) {
                throw new CargaFallida(modelo.nombreVisible(), e.getMessage());
            } catch (IOException | RuntimeException e) {
                LOG.error("Error inesperado al cargar el modelo {}", modelo.nombreVisible(), e);
                throw new CargaFallida(modelo.nombreVisible(), "error inesperado");
            }
        }
        return new ResumenCarga(cuenta.modelosNuevos, cuenta.modelosCompletados, cuenta.pieles, cuenta.colores,
                cuenta.fotos, cuenta.fichas, cuenta.avisos);
    }

    private static final class Cuenta {
        int modelosNuevos;
        int modelosCompletados;
        int pieles;
        int colores;
        int fotos;
        int fichas;
        final List<String> avisos = new ArrayList<>();
    }

    private void cargar(ModeloEnCarga enCarga, Cuenta cuenta) throws IOException {
        String cliente = enCarga.cliente;
        // Se vuelve a mirar: alguien ha podido darlo de alta mientras se revisaba el lote.
        Optional<Modelo> guardado = buscar(enCarga);
        Modelo modelo;
        if (guardado.isEmpty()) {
            modelo = modelos.darDeAlta(new Modelos.AltaManual(cliente, enCarga.temporadaId,
                    vacioANull(enCarga.codigo), vacioANull(enCarga.nombrePrototipo), enCarga.nombre, enCarga.tipo,
                    enCarga.etapa), null);
            cuenta.modelosNuevos++;
        } else {
            modelo = guardado.get();
            completar(modelo, enCarga);
            cuenta.modelosCompletados++;
        }
        Long modeloId = modelo.getId();
        // Los artículos se guardan por referencia: un prototipo sin referencia todavía no tiene.
        if (modelo.getCodigo() != null) {
            Map<String, String> codigosDeColor = new LinkedHashMap<>();
            for (PielEnCarga piel : enCarga.pieles) {
                cargarPiel(enCarga, modeloId, piel, codigosDeColor, cuenta);
            }
            tablaColores.incorporar(cliente, codigosDeColor);
        }
        cargarFotos(enCarga, modeloId, cuenta);
        for (FichaEnCarga ficha : enCarga.fichas) {
            documentos.guardar(modeloId, ficha.nombre, "application/pdf", Files.readAllBytes(ficha.pdf), null);
            cuenta.fichas++;
        }
    }

    private Optional<Modelo> buscar(ModeloEnCarga enCarga) {
        if (!enCarga.codigo.isEmpty()) {
            return repositorioModelos.findByClienteAndCodigo(enCarga.cliente, enCarga.codigo);
        }
        if (enCarga.modeloId != null) {
            return repositorioModelos.findById(enCarga.modeloId);
        }
        if (enCarga.temporadaId != null) {
            return repositorioModelos.findByClienteAndNombrePrototipoAndTemporadaAltaId(enCarga.cliente,
                    enCarga.nombrePrototipo, enCarga.temporadaId);
        }
        return Optional.empty();
    }

    /** Lo del modelo que ya existía: el nombre rellena su hueco; lo tocado pisa; la etapa solo si se ha tocado. */
    private void completar(Modelo modelo, ModeloEnCarga enCarga) {
        Long id = modelo.getId();
        boolean nombreVacio = modelo.getNombre() == null || modelo.getNombre().isEmpty();
        if (!enCarga.nombre.isEmpty() && !enCarga.nombre.equals(modelo.getNombre())
                && (nombreVacio || enCarga.tocado("nombre"))) {
            modelos.editar(id, enCarga.nombre, null);
        }
        if (enCarga.tocado("tipo") && enCarga.tipo != modelo.getTipo()) {
            modelos.editar(id, null, enCarga.tipo);
        }
        if (enCarga.tocado("etapa") && enCarga.etapa != modelo.getEtapa()) {
            modelos.cambiarEtapa(id, enCarga.etapa, MOTIVO_ETAPA, null);
        }
        catalogo.vincularTemporada(id, enCarga.temporadaId);
    }

    private void cargarPiel(ModeloEnCarga enCarga, Long modeloId, PielEnCarga piel,
                            Map<String, String> codigosDeColor, Cuenta cuenta) {
        String cliente = enCarga.cliente;
        boolean habia = catalogo.articulo(cliente, piel.referencia).isPresent();
        Articulo articulo = catalogo.asegurarArticulo(cliente, piel.referencia);
        if (!modeloId.equals(articulo.getModeloId())) {
            throw new IllegalArgumentException("la referencia " + piel.referencia + " ya es de otro modelo");
        }
        if (!habia) {
            cuenta.pieles++;
        }
        List<String> combinaciones = articulo.getCasillasCombinacion();
        escribir(enCarga, piel, "forro", CampoArticulo.FORRO, piel.forro, articulo.getForro());
        CampoArticulo[] deCombinacion = {CampoArticulo.COMBINACION1, CampoArticulo.COMBINACION2,
                CampoArticulo.COMBINACION3, CampoArticulo.COMBINACION4};
        for (int i = 0; i < deCombinacion.length; i++) {
            escribir(enCarga, piel, "combinacion" + i, deCombinacion[i], piel.combinaciones.get(i),
                    i < combinaciones.size() ? combinaciones.get(i) : "");
        }
        escribir(enCarga, piel, "medidaCaja", CampoArticulo.MEDIDA_CAJA, piel.medidaCaja,
                articulo.getMedidaCaja());
        escribir(enCarga, piel, "unidadesPorCaja", CampoArticulo.UNIDADES_POR_CAJA, piel.unidadesPorCaja,
                articulo.getUnidadesPorCaja() == null ? "" : String.valueOf(articulo.getUnidadesPorCaja()));
        escribir(enCarga, piel, "pesoNetoUnidadKg", CampoArticulo.PESO_NETO_UNIDAD_KG, piel.pesoNetoUnidadKg,
                articulo.getPesoNetoUnidadKg() == null ? "" : String.valueOf(articulo.getPesoNetoUnidadKg()));

        if (!piel.refPiel.isEmpty()) {
            String guardado = pieles.findById(new PielId(cliente, piel.refPiel)).map(Piel::getNombre).orElse("");
            if (seEscribe(enCarga, piel.claveCampo("nombrePiel"), piel.nombrePiel, guardado)) {
                memoriaPieles.guardarPiel(cliente, piel.refPiel, piel.nombrePiel);
            }
        }

        Set<String> guardados = new HashSet<>();
        Map<String, ArticuloColor> porClave = new LinkedHashMap<>();
        for (ArticuloColor color : catalogo.coloresDe(cliente, piel.referencia)) {
            guardados.add(color.getCodigoColor());
            porClave.put(color.getCodigoColor(), color);
        }
        CampoColor[] coloresDeCombinacion = {CampoColor.COLOR_COMBINACION1, CampoColor.COLOR_COMBINACION2,
                CampoColor.COLOR_COMBINACION3, CampoColor.COLOR_COMBINACION4};
        for (ColorEnCarga color : piel.colores) {
            ArticuloColor guardado = porClave.get(color.clave);
            if (guardado == null) {
                guardado = catalogo.asegurarColor(cliente, piel.referencia, color.clave);
                cuenta.colores++;
            }
            escribirColor(enCarga, piel, color, "colorPiel", CampoColor.COLOR_PIEL, color.colorPiel,
                    guardado.getColorPiel());
            escribirColor(enCarga, piel, color, "colorForro", CampoColor.COLOR_FORRO, color.colorForro,
                    guardado.getColorForro());
            List<String> deCombinacionGuardados = guardado.getColoresCombinacion();
            for (int i = 0; i < coloresDeCombinacion.length; i++) {
                escribirColor(enCarga, piel, color, "colorCombinacion" + i, coloresDeCombinacion[i],
                        color.coloresCombinacion.get(i),
                        i < deCombinacionGuardados.size() ? deCombinacionGuardados.get(i) : "");
            }
            if (!color.codigo.isEmpty()) {
                if (enCarga.tocado(color.claveCampo(piel, "descripcion"))) {
                    // La descripción la ha corregido una persona aquí: pisa la de la tabla de colores.
                    tablaColores.guardar(cliente, color.codigo, color.descripcion);
                } else {
                    // Si no, solo entra el código que faltaba o se rellena su hueco.
                    codigosDeColor.merge(color.codigo, color.descripcion,
                            (antes, nueva) -> antes.isEmpty() ? nueva : antes);
                }
            }
        }
    }

    private void escribir(ModeloEnCarga enCarga, PielEnCarga piel, String campo, CampoArticulo enCatalogo,
                          String valor, String guardado) {
        if (seEscribe(enCarga, piel.claveCampo(campo), valor, guardado)) {
            catalogo.guardarCampo(enCarga.cliente, piel.referencia, enCatalogo, valor, false);
        }
    }

    private void escribirColor(ModeloEnCarga enCarga, PielEnCarga piel, ColorEnCarga color, String campo,
                               CampoColor enCatalogo, String valor, String guardado) {
        if (seEscribe(enCarga, color.claveCampo(piel, campo), valor, guardado)) {
            catalogo.guardarCampoColor(enCarga.cliente, piel.referencia, color.clave, enCatalogo, valor);
        }
    }

    /** La regla: se escribe si cambia algo y, además, lo guardado estaba vacío o el campo se ha tocado. */
    private static boolean seEscribe(ModeloEnCarga enCarga, String campo, String valor, String guardado) {
        String elGuardado = guardado == null ? "" : guardado;
        if (valor.equals(elGuardado)) {
            return false;
        }
        return elGuardado.isEmpty() || enCarga.tocado(campo);
    }

    /**
     * Guarda las fotos nuevas seleccionadas y deja todas en el orden de la
     * tira: la primera es la principal. Una foto que no se pudo convertir no
     * se carga y se avisa; las demás siguen.
     */
    private void cargarFotos(ModeloEnCarga enCarga, Long modeloId, Cuenta cuenta) throws IOException {
        List<ResumenFoto> yaGuardadas = fotosCatalogo.de(modeloId);
        Set<Long> existentes = new HashSet<>();
        Set<String> usados = new HashSet<>();
        for (ResumenFoto foto : yaGuardadas) {
            existentes.add(foto.id());
            usados.add(foto.nombreFichero().toLowerCase(Locale.ROOT));
        }
        List<Long> orden = new ArrayList<>();
        boolean hayNuevas = false;
        for (FotoEnCarga foto : enCarga.fotos) {
            if (foto.origen == FotoEnCarga.Origen.GUARDADA) {
                if (existentes.contains(foto.fotoCatalogoId)) {
                    orden.add(foto.fotoCatalogoId);
                }
                continue;
            }
            if (!foto.seleccionada) {
                continue;
            }
            Optional<Path> jpeg = foto.jpeg();
            if (jpeg.isEmpty()) {
                cuenta.avisos.add("La foto " + foto.nombre + " de " + enCarga.nombreVisible()
                        + " no se pudo preparar y no se ha cargado");
                continue;
            }
            String libre = FotoCatalogo.nombreLibre(foto.nombre, usados);
            usados.add(libre.toLowerCase(Locale.ROOT));
            orden.add(fotosCatalogo.guardar(modeloId, libre, Files.readAllBytes(jpeg.get())).id());
            cuenta.fotos++;
            hayNuevas = true;
        }
        if (orden.isEmpty() || !(hayNuevas || enCarga.ordenTocado)) {
            return;
        }
        // Las que alguien haya subido mientras tanto por otro lado van detrás: ordenar las exige todas.
        yaGuardadas.stream().map(ResumenFoto::id).filter(id -> !orden.contains(id)).forEach(orden::add);
        fotosCatalogo.ordenar(modeloId, orden);
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isEmpty() ? null : texto;
    }
}
