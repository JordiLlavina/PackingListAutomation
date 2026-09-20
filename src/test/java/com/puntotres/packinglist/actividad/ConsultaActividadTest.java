package com.puntotres.packinglist.actividad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioRepository;

@SpringBootTest
class ConsultaActividadTest {

    @Autowired
    private ConsultaActividad consulta;

    @Autowired
    private RegistroActividad registro;

    @Autowired
    private EventoActividadRepository eventos;

    @Autowired
    private UsuarioRepository usuarios;

    private Usuario ana;
    private Usuario luis;

    @BeforeEach
    void sembrar() {
        eventos.deleteAll();
        usuarios.deleteAll();
        ana = usuarios.saveAndFlush(new Usuario("oid-1", "ana@puntotres.com", "Ana"));
        luis = usuarios.saveAndFlush(new Usuario("oid-2", "luis@puntotres.com", "Luis"));

        registro.registrarPara(ana, TipoEvento.ENTRADA, null, null, null, null);
        registro.registrarPara(ana, TipoEvento.PACKING_LIST_GENERADO, Seccion.PACKING_LIST,
                "AMI", 4, null);
        registro.registrarPara(ana, TipoEvento.PACKING_LIST_GENERADO, Seccion.PACKING_LIST,
                "APC", 3, null);
        registro.registrarPara(luis, TipoEvento.ENTRADA, null, null, null, null);
        registro.registrarPara(luis, TipoEvento.ESCANDALLOS_PROCESADOS, Seccion.ESCANDALLOS,
                null, 2, null);
    }

    @Test
    void lasCuatroCifrasDelPeriodo() {
        ResumenActividad resumen = consulta.resumir(FiltroActividad.ultimos30Dias());

        assertEquals(2, resumen.personas());
        assertEquals(2, resumen.accesos());
        // Documentos = la suma de "cantidad" de los tipos que producen
        // documentos: 4 + 3 packing lists + 2 escandallos.
        assertEquals(9, resumen.documentos());
    }

    @Test
    void elDesgloseporClienteSumaLasCantidades() {
        ResumenActividad resumen = consulta.resumir(FiltroActividad.ultimos30Dias());

        assertEquals(2, resumen.porCliente().size());
        assertEquals("AMI", resumen.porCliente().get(0).cliente());
        assertEquals(4, resumen.porCliente().get(0).documentos());
    }

    @Test
    void filtrarPorUsuarioDejaSoloLoSuyo() {
        ResumenActividad resumen = consulta.resumir(new FiltroActividad(
                LocalDate.now().minusDays(30), LocalDate.now(), luis.getId(), null));

        assertEquals(1, resumen.personas());
        assertEquals(2, resumen.documentos());
    }

    @Test
    void filtrarPorSeccionDejaSoloEsaSeccion() {
        ResumenActividad resumen = consulta.resumir(new FiltroActividad(
                LocalDate.now().minusDays(30), LocalDate.now(), null, Seccion.ESCANDALLOS));

        assertEquals(1, resumen.porSeccion().size());
        assertEquals(2, resumen.documentos());
    }

    @Test
    void unRangoQueNoIncluyeHoyNoTraeNada() {
        ResumenActividad resumen = consulta.resumir(new FiltroActividad(
                LocalDate.now().minusDays(90), LocalDate.now().minusDays(60), null, null));

        assertEquals(0, resumen.accesos());
        assertEquals(0, resumen.cronologia().size());
    }

    @Test
    void elDiaDeHoyEntraEnElRangoQueTerminaHoy() {
        // El borde que más se equivoca: "hasta hoy" tiene que incluir lo de
        // hoy, y un rango construido con la fecha a secas se queda en la
        // medianoche y deja fuera toda la jornada.
        ResumenActividad resumen = consulta.resumir(new FiltroActividad(
                LocalDate.now(), LocalDate.now(), null, null));

        assertEquals(5, resumen.cronologia().size());
    }
}
