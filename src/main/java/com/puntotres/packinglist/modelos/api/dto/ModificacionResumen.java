package com.puntotres.packinglist.modelos.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.puntotres.packinglist.modelos.persistence.Direccion;
import com.puntotres.packinglist.modelos.persistence.EstadoModificacion;
import com.puntotres.packinglist.modelos.persistence.Prioridad;
import com.puntotres.packinglist.modelos.persistence.Rol;

/**
 * Una modificación tal como sale en una bandeja o en la cabecera de su
 * detalle.
 *
 * @param modeloReferencia     el código del modelo o, si no lo tiene, su nombre de prototipo
 * @param fotoPrincipalId      la foto que representa al modelo; null si no tiene ninguna
 * @param tipoCambioTexto      el texto del tipo de cambio en el yml; la clave si ya no está
 * @param creadaPor            el nombre de quien la creó; null si no la creó una persona
 * @param respuestaPendienteDe el lado que tiene que contestar; null si nadie
 * @param vencida              se le ha pasado el plazo a quien tiene que actuar
 * @param destinos             dónde se aplica; vacío en las órdenes para el taller
 * @param fotos                fotos de todos sus mensajes (y lo mismo audios y documentos)
 * @param duracionAudioSeg     cuánto dura la primera nota de voz del primer mensaje; null si no tiene
 * @param ultimoMensajeDe      de qué lado es el último mensaje (TALLER o DESPACHO; un ADMIN habla por el
 *                             despacho), también en una cerrada; null si no hay ninguno
 */
public record ModificacionResumen(Long id, Long modeloId, String modeloReferencia, String modeloNombre,
                                  String cliente, Long fotoPrincipalId, Direccion direccion, String tipoCambio,
                                  String tipoCambioTexto, Prioridad prioridad, EstadoModificacion estado,
                                  String titulo, boolean tituloDeIa, LocalDateTime creadaEn, String creadaPor,
                                  LocalDateTime vistaEn, LocalDateTime hechaEn, String hechaPor,
                                  Rol respuestaPendienteDe, boolean vencida, List<DestinoEstado> destinos, int fotos,
                                  int audios, int documentos, Integer duracionAudioSeg, Rol ultimoMensajeDe) {
}
