package com.puntotres.packinglist.shipping.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.config.EnviosProperties;
import com.puntotres.packinglist.shipping.model.*;

/**
 * La máquina de estados del envío:
 *
 *   DRAFT ─tarifar→ QUOTED ─confirmar→ CONFIRMED ─crear→ CREATED | ERROR ─crear→ ...
 *   editar desde QUOTED vuelve a DRAFT; cancelar desde todo menos CREATED/CANCELLED.
 *
 * Confirmar persiste el requestId ANTES de hablar con el transportista, y
 * crear lo reutiliza: así un envío confirmado nunca se crea dos veces aunque
 * el proceso muera a medias o el usuario haga doble clic (@Version en Shipment
 * hace que el segundo clic falle con una excepción de concurrencia).
 */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository envios;
    private final ContactRepository contactos;
    private final BillingAccountRepository cuentas;
    private final ShipmentReasonRepository motivos;
    private final ArticleRepository articulos;
    private final ShipmentDocumentRepository documentos;
    private final CarrierClientRegistry transportistas;
    private final Notifier notifier;
    private final DirectionResolver sentidos;
    private final CustomsChecker aduanas;
    private final EnviosProperties propiedades;

    public ShipmentService(ShipmentRepository envios, ContactRepository contactos, BillingAccountRepository cuentas,
                           ShipmentReasonRepository motivos, ArticleRepository articulos, ShipmentDocumentRepository documentos,
                           CarrierClientRegistry transportistas, Notifier notifier,
                           DirectionResolver sentidos, CustomsChecker aduanas, EnviosProperties propiedades) {
        this.envios = envios; this.contactos = contactos; this.cuentas = cuentas; this.motivos = motivos;
        this.articulos = articulos; this.documentos = documentos; this.transportistas = transportistas;
        this.notifier = notifier; this.sentidos = sentidos; this.aduanas = aduanas; this.propiedades = propiedades;
    }

    @Transactional
    public Shipment crearBorrador(String responsable) {
        Shipment envio = new Shipment(responsable);
        envio.setMoneda(propiedades.getMoneda());
        envio.setPedirRecogida(true);
        envio.setHoraCierreRecogida(propiedades.getHoraCierreRecogida());
        envio.setLugarRecogida(propiedades.getLugarRecogida());
        envio.cambiarEstado(ShipmentStatus.DRAFT, responsable, "Borrador creado");
        return envios.save(envio);
    }

    /**
     * El envío con sus bultos, líneas de aduana, ofertas e historial ya
     * inicializados (open-in-view está apagado, así que sin esto la vista
     * lanzaría LazyInitializationException al recorrerlos). Los documentos
     * NO se cargan aquí a propósito: son PDF y pesan, y la pantalla solo
     * necesita saber qué tipos hay (tiposDocumento) para pintar los botones
     * y trae el PDF concreto que se pulse (documento) bajo demanda.
     */
    @Transactional(readOnly = true)
    public Shipment cargar(Long id) {
        Shipment envio = buscar(id);
        inicializar(envio);
        return envio;
    }

    /**
     * Igual que cargar(), para toda la lista: la pantalla de listado solo
     * pinta columnas sueltas y nunca lo necesitó, pero la API sí monta un DTO
     * completo por envío (bultos, líneas, ofertas, historial), así que sin
     * esto cada entidad llegaría con esas colecciones lazy sin inicializar.
     * El servicio es el dueño de la carga perezosa porque open-in-view está
     * apagado y los controladores no deben abrir transacciones propias: una
     * sola regla para cualquier llamador, en vez de que cada controlador
     * decida por su cuenta qué inicializar.
     */
    @Transactional(readOnly = true)
    public List<Shipment> listar(FiltroEnvios filtro) {
        Specification<Shipment> spec = (raiz, consulta, cb) -> {
            List<jakarta.persistence.criteria.Predicate> condiciones = new ArrayList<>();
            if (filtro.desde() != null) condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaCreacion"), filtro.desde().atStartOfDay()));
            if (filtro.hasta() != null) condiciones.add(cb.lessThan(raiz.get("fechaCreacion"), filtro.hasta().plusDays(1).atStartOfDay()));
            if (filtro.estado() != null) condiciones.add(cb.equal(raiz.get("estado"), filtro.estado()));
            if (filtro.motivoId() != null) condiciones.add(cb.equal(raiz.get("motivo").get("id"), filtro.motivoId()));
            if (filtro.cliente() != null && !filtro.cliente().isBlank())
                condiciones.add(cb.like(cb.upper(raiz.get("cliente")), "%" + filtro.cliente().trim().toUpperCase() + "%"));
            if (filtro.sentido() != null) condiciones.add(cb.equal(raiz.get("sentido"), filtro.sentido()));
            consulta.orderBy(cb.desc(raiz.get("fechaCreacion")));
            return cb.and(condiciones.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<Shipment> resultado = envios.findAll(spec);
        resultado.forEach(this::inicializar);
        return resultado;
    }

    private void inicializar(Shipment envio) {
        envio.getBultos().size();
        envio.getLineasAduana().size();
        envio.getOfertas().size();
        envio.getHistorial().size();
    }

    /**
     * Igual que responsable, horaCierreRecogida y lugarRecogida solo se tocan
     * si datos las trae (null significa "no tocar" y conserva lo que ya
     * tuviera el envío, típicamente lo que puso crearBorrador); una cadena
     * vacía sí las borra, vía limpiar().
     *
     * <p>Las cuentas y el incoterm del destinatario se heredan UNA SOLA VEZ, en
     * el guardado donde el destinatario cambia (o el envío no tenía ninguno
     * todavía), y la herencia SOLO RELLENA HUECOS: se copia el valor por
     * defecto del destinatario nuevo a un campo únicamente si ese campo llegó
     * vacío en {@code datos} ({@code cuentaTransporteId}/{@code
     * cuentaArancelesId}/{@code incoterm} a {@code null}) y el destinatario sí
     * trae un valor por defecto. Lo que llega explícito en {@code datos} manda
     * siempre, coincida o no con el del contacto: un {@code POST}/{@code PUT}
     * con una cuenta de aranceles elegida a mano, o un guardado del formulario
     * que fija remitente+destinatario+cuenta de una vez, no pierde esa
     * elección — pisarla contradiría el contrato de "el PUT reemplaza con lo
     * que traiga" que documenta el README de la sección. En guardados
     * siguientes con el mismo destinatario ya no se vuelve a heredar: manda lo
     * que teclee el usuario, incluido dejar el campo vacío.
     *
     * <p>Un id que no existe (remitente, destinatario, cuentas, motivo o el
     * artículo de una línea) no se traga en silencio como {@code null}: quien
     * lo mandó tecleó o programó algo que ya no está, y seguir adelante
     * guardaría un envío con un contacto o una cuenta distintos de los que
     * creía estar eligiendo.
     */
    @Transactional
    public Shipment editar(Long id, DatosEnvio datos) {
        Shipment envio = buscar(id);
        if (!envio.editable()) {
            throw new TransicionIlegalException("El envío " + id + " está en " + envio.getEstado() + " y ya no se edita");
        }
        Contact destinatarioAnterior = envio.getDestinatario();

        envio.setRemitente(buscarContacto(datos.remitenteId(), "remitente"));
        Contact nuevoDestinatario = buscarContacto(datos.destinatarioId(), "destinatario");
        envio.setDestinatario(nuevoDestinatario);
        envio.setFechaRecogida(datos.fechaRecogida());
        if (datos.incoterm() != null) envio.setIncoterm(datos.incoterm());
        envio.setCuentaTransporte(buscarCuenta(datos.cuentaTransporteId()));
        envio.setCuentaAranceles(buscarCuenta(datos.cuentaArancelesId()));
        envio.setMotivo(buscarMotivo(datos.motivoId()));
        envio.setCliente(limpiar(datos.cliente()));
        envio.setTemporada(limpiar(datos.temporada()));
        envio.setReferencia(limpiar(datos.referencia()));
        envio.setNotas(limpiar(datos.notas()));
        if (datos.responsable() != null && !datos.responsable().isBlank()) envio.setResponsable(datos.responsable());
        envio.setPedirRecogida(datos.pedirRecogida() == null || datos.pedirRecogida());
        if (datos.horaCierreRecogida() != null) envio.setHoraCierreRecogida(limpiar(datos.horaCierreRecogida()));
        if (datos.lugarRecogida() != null) envio.setLugarRecogida(limpiar(datos.lugarRecogida()));

        envio.getBultos().clear();
        for (DatosEnvio.DatosBulto b : datos.bultos()) {
            envio.getBultos().add(new ShipmentPackage(b.peso(), b.largo(), b.ancho(), b.alto(), limpiar(b.descripcion())));
        }
        envio.getLineasAduana().clear();
        for (DatosEnvio.DatosLinea l : datos.lineasAduana()) {
            Article articulo = buscarArticulo(l.articuloId());
            envio.getLineasAduana().add(new CustomsLineItem(limpiar(l.descripcion()), limpiar(l.codigoHs()), l.cantidad(),
                    l.valorUnitario(), l.pesoNeto(), l.pesoBruto(), limpiar(l.paisOrigen()), articulo));
        }

        // Contact no redefine equals(): comparar por identidad es lo correcto tanto en
        // producción (un mismo id dentro de una transacción JPA resuelve al mismo
        // objeto gestionado, por la caché de primer nivel) como en los tests con dobles,
        // y evita un NullPointerException con contactos sin persistir (id == null) que
        // sí pueden llegar en los tests aunque nunca en producción.
        boolean cambioDestinatario = nuevoDestinatario != null && nuevoDestinatario != destinatarioAnterior;
        if (cambioDestinatario) {
            // Rellena huecos, nunca pisa: si datos trajo un valor explícito para el
            // campo (cuenta elegida a mano, incoterm del formulario), ese manda siempre
            // — la herencia solo entra donde el llamador no dijo nada.
            if (datos.cuentaTransporteId() == null && nuevoDestinatario.getCuentaTransportePorDefecto() != null)
                envio.setCuentaTransporte(nuevoDestinatario.getCuentaTransportePorDefecto());
            if (datos.cuentaArancelesId() == null && nuevoDestinatario.getCuentaArancelesPorDefecto() != null)
                envio.setCuentaAranceles(nuevoDestinatario.getCuentaArancelesPorDefecto());
            if (datos.incoterm() == null && nuevoDestinatario.getIncotermPorDefecto() != null)
                envio.setIncoterm(nuevoDestinatario.getIncotermPorDefecto());
        }

        // Sentido y aduana se deducen siempre de los países: no son opinables y
        // desactivar la aduana a mano dejaría un envío internacional sin declarar.
        String origen = envio.getRemitente() == null ? null : envio.getRemitente().getPais();
        String destino = envio.getDestinatario() == null ? null : envio.getDestinatario().getPais();
        envio.setSentido(sentidos.resolver(origen, destino));
        envio.setAduanero(aduanas.esAduanero(origen, destino));

        if (envio.getEstado() == ShipmentStatus.QUOTED) {
            envio.borrarOfertas();
            envio.setCosteEstimado(null);
            envio.cambiarEstado(ShipmentStatus.DRAFT, envio.getResponsable(), "Editado tras tarifar: la tarifa ya no vale");
        }
        envio.tocar();
        return envios.save(envio);
    }

    private Contact buscarContacto(Long id, String rol) {
        if (id == null) return null;
        return contactos.findById(id).orElseThrow(() -> new DatoInvalidoException("No existe el contacto " + id + " (" + rol + ")"));
    }

    private BillingAccount buscarCuenta(Long id) {
        if (id == null) return null;
        return cuentas.findById(id).orElseThrow(() -> new DatoInvalidoException("No existe la cuenta " + id));
    }

    private ShipmentReason buscarMotivo(Long id) {
        if (id == null) return null;
        return motivos.findById(id).orElseThrow(() -> new DatoInvalidoException("No existe el motivo " + id));
    }

    private Article buscarArticulo(Long id) {
        if (id == null) return null;
        return articulos.findById(id).orElseThrow(() -> new DatoInvalidoException("No existe el artículo " + id));
    }

    /** Lo que impide tarifar. Vacío = se puede. */
    public List<String> problemasParaTarifar(Shipment envio) {
        List<String> problemas = new ArrayList<>();
        if (envio.getRemitente() == null) problemas.add("Falta el remitente.");
        if (envio.getDestinatario() == null) problemas.add("Falta el destinatario.");
        for (Contact c : List.of(Optional.ofNullable(envio.getRemitente()), Optional.ofNullable(envio.getDestinatario())).stream()
                .flatMap(Optional::stream).toList()) {
            if (c.getTelefono() == null || c.getTelefono().isBlank()) problemas.add("El contacto " + c.getEmpresa() + " no tiene teléfono; DHL lo exige.");
        }
        if (envio.getFechaRecogida() == null) problemas.add("Falta la fecha de recogida.");
        else if (envio.getFechaRecogida().isBefore(java.time.LocalDate.now())) problemas.add("La fecha de recogida ya ha pasado.");
        if (envio.getMotivo() == null) problemas.add("Falta el motivo del envío.");
        if (envio.isPedirRecogida()
                && (envio.getHoraCierreRecogida() == null || !envio.getHoraCierreRecogida().matches("([0-1][0-9]|2[0-3]):[0-5][0-9]")))
            problemas.add("Se pide recogida y falta la hora de cierre (HH:MM).");
        if (envio.getResponsable() == null || envio.getResponsable().isBlank()) problemas.add("Falta el responsable.");
        if (envio.getBultos().isEmpty()) problemas.add("No hay bultos.");
        for (int i = 0; i < envio.getBultos().size(); i++) {
            if (!envio.getBultos().get(i).completo()) problemas.add("El bulto " + (i + 1) + " no tiene peso o medidas.");
        }
        if (envio.getCuentaTransporte() == null) problemas.add("Falta la cuenta que paga el transporte.");
        else if (!envio.getCuentaTransporte().permite(BillingRole.PAYER)) problemas.add("La cuenta " + envio.getCuentaTransporte().getNumero() + " no puede pagar transporte.");
        if (envio.getCuentaAranceles() != null && !envio.getCuentaAranceles().permite(BillingRole.DUTIES)) problemas.add("La cuenta " + envio.getCuentaAranceles().getNumero() + " no puede pagar aranceles.");
        if (envio.isAduanero()) {
            if (envio.getLineasAduana().isEmpty()) problemas.add("Es un envío con aduana y no hay líneas de mercancía.");
            for (int i = 0; i < envio.getLineasAduana().size(); i++) {
                if (!envio.getLineasAduana().get(i).completa()) problemas.add("La línea de aduana " + (i + 1) + " está incompleta (descripción, código HS, cantidad, valor y país de origen).");
            }
        }
        return problemas;
    }

    /** Lo que conviene mirar pero no impide seguir. */
    public List<String> avisos(Shipment envio) {
        List<String> avisos = new ArrayList<>();
        if (envio.getSentido() == Direction.DOMESTIC) avisos.add("Sentido: Nacional. Los dos contactos están en " + propiedades.getPaisSede() + ".");
        if (envio.getSentido() == Direction.CROSS_TRADE) avisos.add("Sentido: Entre terceros países. El envío ni sale ni entra en " + propiedades.getPaisSede() + ".");
        for (int i = 0; i < envio.getLineasAduana().size(); i++) {
            CustomsLineItem l = envio.getLineasAduana().get(i);
            if (l.getPesoNeto() != null && l.getPesoBruto() != null && l.getPesoNeto() > l.getPesoBruto())
                avisos.add("La línea " + (i + 1) + " pesa más en neto que en bruto.");
        }
        double bultos = envio.pesoBultos();
        double lineas = envio.pesoBrutoLineas();
        if (bultos > 0 && lineas > bultos + 0.001)
            avisos.add(String.format("El peso bruto de las líneas (%.2f kg) supera el de los bultos (%.2f kg).", lineas, bultos));
        return avisos;
    }

    @Transactional
    public ResultadoTarifa tarifar(Long id) {
        Shipment envio = buscar(id);
        if (!envio.editable()) throw new TransicionIlegalException("Solo se tarifa un borrador; el envío está en " + envio.getEstado());
        List<String> problemas = problemasParaTarifar(envio);
        if (!problemas.isEmpty()) return new ResultadoTarifa(envio, problemas, null);
        try {
            List<ProductOffer> ofertas = transportistas.para(envio.getTransportista()).quote(envio);
            envio.reemplazarOfertas(ofertas.stream()
                    .map(o -> new RateOffer(o.codigo(), o.nombre(), o.precio(), o.moneda(), o.entregaEstimada())).toList());
            envio.setUltimoError(null);
            envio.cambiarEstado(ShipmentStatus.QUOTED, envio.getResponsable(), ofertas.size() + " productos disponibles");
            return new ResultadoTarifa(envios.save(envio), List.of(), null);
        } catch (CarrierException e) {
            envio.setUltimoError(e.getMensajeUsuario());
            envios.save(envio);
            return new ResultadoTarifa(envio, List.of(), e.getMensajeUsuario());
        }
    }

    @Transactional
    public Shipment confirmar(Long id, String productoCodigo) {
        Shipment envio = buscar(id);
        if (envio.getEstado() != ShipmentStatus.QUOTED) throw new TransicionIlegalException("Solo se confirma un envío tarifado; está en " + envio.getEstado());
        RateOffer elegida = envio.getOfertas().stream().filter(o -> o.getProductoCodigo().equals(productoCodigo)).findFirst()
                .orElseThrow(() -> new TransicionIlegalException("El producto " + productoCodigo + " no está entre los tarifados"));
        envio.setProductoCodigo(elegida.getProductoCodigo());
        envio.setProductoNombre(elegida.getProductoNombre());
        envio.setCosteEstimado(elegida.getPrecio());
        if (elegida.getMoneda() != null) envio.setMoneda(elegida.getMoneda());
        envio.setRequestId(UUID.randomUUID().toString());
        envio.cambiarEstado(ShipmentStatus.CONFIRMED, envio.getResponsable(), "Producto " + elegida.getProductoNombre());
        return envios.save(envio);
    }

    @Transactional
    public Shipment crear(Long id) {
        Shipment envio = buscar(id);
        if (envio.getEstado() != ShipmentStatus.CONFIRMED && envio.getEstado() != ShipmentStatus.ERROR)
            throw new TransicionIlegalException("Solo se crea un envío confirmado; está en " + envio.getEstado());
        try {
            CreatedShipment creado = transportistas.para(envio.getTransportista()).createShipment(envio);
            envio.setNumeroGuia(creado.numeroGuia());
            envio.setUrlSeguimiento(creado.urlSeguimiento());
            envio.setNumeroConfirmacionRecogida(creado.numeroConfirmacionRecogida());
            envio.getDocumentos().clear();
            for (CarrierDocument d : creado.documentos()) envio.anadirDocumento(d.tipo(), d.formato(), d.contenido());
            envio.setUltimoError(null);
            envio.cambiarEstado(ShipmentStatus.CREATED, envio.getResponsable(), "Guía " + creado.numeroGuia());
            Shipment guardado = envios.save(envio);
            // Un aviso que falla (SMTP caído, Telegram sin responder...) no puede deshacer
            // un envío que ya existe en DHL: el estado se queda guardado y solo se registra.
            try {
                notifier.envioCreado(guardado);
            } catch (RuntimeException e) {
                log.warn("El aviso del envío {} ha fallado: {}", envio.getId(), e.getMessage(), e);
            }
            return guardado;
        } catch (CarrierException e) {
            return marcarError(envio, e.getMensajeUsuario());
        } catch (RuntimeException e) {
            // Un fallo que no es CarrierException (un mapeo roto, un NullPointerException
            // con un contrato de DHL que cambió) no puede tirar la transacción con un 500:
            // merece el mismo tratamiento que un fallo reconocido del transportista, con
            // el mensaje técnico delante para el log.
            log.warn("Fallo inesperado creando el envío {}: {}", envio.getId(), e.getMessage(), e);
            return marcarError(envio, "Error inesperado hablando con el transportista: " + e.getMessage());
        }
    }

    /** Cuerpo compartido por los dos catch de crear(): deja el envío en ERROR con el mensaje y avisa. */
    private Shipment marcarError(Shipment envio, String mensaje) {
        envio.setUltimoError(mensaje);
        envio.cambiarEstado(ShipmentStatus.ERROR, envio.getResponsable(), mensaje);
        Shipment guardado = envios.save(envio);
        try {
            notifier.envioFallido(guardado, mensaje);
        } catch (RuntimeException ex) {
            log.warn("El aviso del envío {} ha fallado: {}", envio.getId(), ex.getMessage(), ex);
        }
        return guardado;
    }

    @Transactional
    public Shipment cancelar(Long id) {
        Shipment envio = buscar(id);
        if (envio.getEstado() == ShipmentStatus.CREATED || envio.getEstado() == ShipmentStatus.CANCELLED)
            throw new TransicionIlegalException("Un envío en " + envio.getEstado() + " no se cancela desde aquí");
        envio.cambiarEstado(ShipmentStatus.CANCELLED, envio.getResponsable(), null);
        return envios.save(envio);
    }

    @Transactional(readOnly = true)
    public Optional<ShipmentDocument> documento(Long id, String tipo) {
        return documentos.findFirstByEnvioIdAndTipo(id, tipo);
    }

    @Transactional(readOnly = true)
    public List<String> tiposDocumento(Long id) {
        return documentos.tiposDe(id);
    }

    private Shipment buscar(Long id) {
        return envios.findById(id).orElseThrow(() -> new EnvioNoEncontradoException(id));
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
