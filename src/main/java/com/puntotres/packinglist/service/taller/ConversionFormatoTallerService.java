package com.puntotres.packinglist.service.taller;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.service.VolcadoExcel;

/**
 * "Excel Formato Taller" de la entrada por taller: un taller que hace el
 * packing a su manera (Fez con su DESIGNATION que mezcla color y destinación,
 * Tánger con su "Korea 690" y su tabla de cajas con defectos debajo...) manda
 * su excel, y aquí se pasa al formato Puntotres para que siga por la
 * digestión de siempre ({@link TallerColisExcel} → {@link DigestionTallerService}).
 *
 * El reparto de trabajo es el que hace que el resultado sea auditable:
 * <ul>
 * <li><b>Claude lee y separa</b>: elige la hoja, parte "SAC LAW GRIS WSALES"
 *     en color y destinación, hereda celdas vacías, aparta las notas. Es lo
 *     flexible, lo que cambia de un taller a otro, y lo que una heurística
 *     por formato haría mal (deducir columnas del contenido es justo lo que
 *     {@link TallerColisExcel} prohíbe).</li>
 * <li><b>Java decide</b> ({@link AjustadorConversion}): pasar un nombre de
 *     color a su código, completar la referencia, resolver el código de
 *     pedido. Son consultas a una tabla con una sola respuesta, y el prompt
 *     le prohíbe al modelo hacerlas.</li>
 * </ul>
 * Lo que viaja: el índice del pedido ({@link IndiceCatalogoConversion}), el
 * libro entero como texto celda a celda, y el prompt del cliente. Lo que
 * vuelve es un JSON con el esquema {@link #ESQUEMA} (salida estructurada:
 * la API garantiza que es válido), que se lee como
 * {@link PackingFormatoPuntotres}; el excel lo escribe
 * {@link FormatoPuntotresExcelBuilder}, nunca el modelo.
 *
 * Ejemplos reales de los dos formatos que había al escribirlo en
 * docs/Packing Lists/Packing List Excel Taller tests/.
 *
 * El cliente HTTP se crea perezosamente en el primer uso, como en
 * {@code ClaudeEnvioExtractionService}: la aplicación arranca aunque no esté
 * configurada ANTHROPIC_API_KEY.
 */
@Service
public class ConversionFormatoTallerService {

    /** Fallo de conversión con mensaje pensado para mostrarse al usuario. */
    public static class ConversionException extends RuntimeException {
        public ConversionException(String mensaje) { super(mensaje); }
        public ConversionException(String mensaje, Throwable causa) { super(mensaje, causa); }
    }

    /** Tokens de entrada y salida de la llamada, para medir lo que cuesta. */
    public record Uso(long tokensEntrada, long tokensSalida) {
    }

    /** El resultado: lo leído y ajustado, el excel ya escrito, y lo que ha costado. */
    public record Resultado(PackingFormatoPuntotres packing, byte[] excel, Uso uso) {
    }

    static final String MODELO = "claude-opus-5-5";

    /**
     * Razonamiento y JSON comparten el techo. La salida en sí es corta (una
     * fila por línea del taller), pero cuadrar rangos de cajas y cantidades
     * consume razonamiento; con streaming el techo alto no cuesta timeouts.
     */
    private static final long TOKENS_MAXIMOS = 64_000L;

    /**
     * HIGH: es una transcripción con reglas, no un problema abierto. Opus 5.5
     * arranca en MEDIUM si no se dice nada, y aquí un descuadre de cantidades
     * acaba en un packing real. Se puede bajar con la propiedad del sistema
     * {@code conversion.esfuerzo} para medirlo (ConversionFormatoTallerRealTool).
     */
    static final OutputConfig.Effort ESFUERZO = esfuerzoConfigurado();

    private static final Duration TIEMPO_MAXIMO = Duration.ofMinutes(15);

    /**
     * Topes del volcado del libro. Un packing de taller real son unas decenas
     * de filas y una docena de columnas; por encima de esto lo subido no es un
     * packing, y mandarlo entero solo llenaría la petición.
     */
    static final int MAXIMO_FILAS_POR_HOJA = 600;
    static final int MAXIMO_COLUMNAS = 40;

    /**
     * El esquema del JSON de respuesta. Tiene que seguir a
     * {@link PackingFormatoPuntotres}: un campo del record que no esté aquí
     * llega siempre a null. Todos los campos son obligatorios y los opcionales
     * admiten null, que es lo que la salida estructurada exige para garantizar
     * la forma; {@code additionalProperties: false} en cada objeto. Solo los
     * NÚMEROS admiten null: la API limita a 16 los campos con unión de tipos,
     * así que un texto que no está es la cadena vacía.
     */
    static final String ESQUEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["hoja", "hojasNoConvertidas", "cabecera", "filas", "notas", "resumen", "avisos"],
              "properties": {
                "hoja": {"type": "string", "description": "Nombre de la hoja convertida"},
                "hojasNoConvertidas": {"type": "array", "items": {"type": "string"},
                  "description": "Hojas que son OTRO packing y se han dejado fuera"},
                "cabecera": {
                  "type": "object", "additionalProperties": false,
                  "required": ["taller", "direccion", "factura", "fecha"],
                  "properties": {
                    "taller": {"type": "string"},
                    "direccion": {"type": "string"},
                    "factura": {"type": "string", "description": "Solo el número, sin la palabra Facture"},
                    "fecha": {"type": "string"}
                  }
                },
                "filas": {
                  "type": "array",
                  "items": {
                    "type": "object", "additionalProperties": false,
                    "required": ["envio", "cliente", "motivo", "referencia", "color", "talla",
                                 "destinacion", "codigoCliente", "unidadesPorCaja", "cantidad",
                                 "cajas", "pesoBrutoCaja", "medidaCaja"],
                    "properties": {
                      "envio": {"type": "string"},
                      "cliente": {"type": "string"},
                      "motivo": {"type": "string"},
                      "referencia": {"type": "string"},
                      "color": {"type": "string"},
                      "talla": {"type": "string"},
                      "destinacion": {"type": "string"},
                      "codigoCliente": {"type": "string"},
                      "unidadesPorCaja": {"type": ["integer", "null"]},
                      "cantidad": {"type": ["integer", "null"]},
                      "cajas": {"type": "string"},
                      "pesoBrutoCaja": {"type": ["number", "null"]},
                      "medidaCaja": {"type": "string"}
                    }
                  }
                },
                "notas": {
                  "type": "array",
                  "items": {
                    "type": "object", "additionalProperties": false,
                    "required": ["celdas", "letra", "fondo"],
                    "properties": {
                      "celdas": {"type": "array", "items": {"type": "string"}, "description": "Las celdas no vacías de la fila, en orden, copiadas tal cual"},
                      "letra": {"type": "string", "description": "El #RRGGBB de [letra ...] de sus celdas; \\"\\" si no lo llevan"},
                      "fondo": {"type": "string", "description": "El #RRGGBB de [fondo ...] de sus celdas; \\"\\" si no lo llevan"}
                    }
                  }
                },
                "resumen": {
                  "type": "object", "additionalProperties": false,
                  "required": ["unidadesTotales", "cajasTotales"],
                  "properties": {
                    "unidadesTotales": {"type": ["integer", "null"], "description": "Total de unidades que DECLARA la hoja, no tu suma"},
                    "cajasTotales": {"type": ["integer", "null"], "description": "Total de cajas que DECLARA la hoja, no tu cuenta"}
                  }
                },
                "avisos": {
                  "type": "array",
                  "items": {
                    "type": "object", "additionalProperties": false,
                    "required": ["fila", "hastaFila", "columna", "texto", "consecuencia"],
                    "properties": {
                      "fila": {"type": ["integer", "null"], "description": "Posición (desde 1) en filas de la fila a la que se refiere; null si habla del fichero entero"},
                      "hastaFila": {"type": ["integer", "null"], "description": "Si la MISMA interpretación vale para varias filas seguidas, la última de ellas (desde 1); null si es solo la de 'fila'"},
                      "columna": {"type": "string", "enum": ["", "envio", "cliente", "motivo", "referencia", "color", "talla", "destinacion", "codigoCliente", "unidadesPorCaja", "cantidad", "cajas", "pesoBrutoCaja", "medidaCaja"], "description": "El campo de la fila con la duda: ahí se pinta la celda y va el comentario. \\"\\" si es de la fila entera o del fichero"},
                      "texto": {"type": "string"},
                      "consecuencia": {"type": "string", "description": "Etiqueta corta (3-6 palabras) de la clase de aviso, la misma para todos los de esa clase: 'envío tomado de la factura', 'caja mixta', 'color traducido', 'color aproximado', 'destinación interpretada', 'celda resaltada por el taller', 'lectura dudosa'. \\"\\" si no encaja en ninguna"}
                    }
                  }
                }
              }
            }
            """;

    static final String PROMPT_NUCLEO = """
            Conviertes el packing list que manda un TALLER de marroquinería (bolsos y \
            cinturones) a Punto Tres al "formato Puntotres", una tabla fija. Cada taller \
            hace el packing a su manera: columnas con otros nombres, datos mezclados en una \
            misma celda, filas que heredan el valor de la de encima, notas sueltas debajo. \
            Tu trabajo es LEER Y SEPARAR; lo que sea una consulta al pedido del cliente \
            (qué número de pedido es, cómo se completa una referencia) lo hace después la \
            aplicación y NO debes hacerlo tú.

            ## La entrada

            El excel del taller viene volcado como texto, hoja a hoja, una línea por fila
            con cada celda no vacía como "LETRA=valor" ("B7=ULL163AL052 | C7=SAC MARRON
            PA"). Las fórmulas vienen ya calculadas. Una celda tachada va como ~~valor~~,
            una con fondo de color lleva [fondo #RRGGBB], una escrita en un color que
            no es el negro lleva [letra #RRGGBB], una con comentario lleva [nota: ...],
            y al principio de cada hoja se listan las celdas combinadas.
            Delante puede venir el PEDIDO del cliente (ver regla 9).

            ## Cómo es el formato Puntotres

            Así se ve un packing ya en formato Puntotres, con la misma notación (una fila
            por grupo de cajas iguales; CODE CLIENT es el código de pedido de 3 cifras de
            APC; AMI no lo lleva):

              B9=PUNTOTRES ENVOI | C9=CLIENT | D9=MOTIF | E9=REFERENCE | F9=COULEUR |
              G9=TAILLE | H9=DESTINATION | I9=CODE CLIENT | J9=QTITE / COLIS |
              K9=QUANTITE | L9=Nº DE COLIS | M9=POIDS BRUT CAISSE | N9=DIMENSIONS CAISSE
              B10=145 | C10=AMI | D10=PROD | E10=ULL163.AL0052 | F10=BLACK | G10=U |
              H10=JA | J10=8 | K10=200 | L10=1-25 | M10=10 | N10=60x40x40
              B12=145 | C12=AMI | D12=PROD | E12=ULL163.AL0052 | F12=DARK COFFEE |
              G12=U | H12=CH | J12=8 | K12=8 | L12=61 | M12=10 | N12=60x40x40
              B15=144 | C15=APC | D15=PROD | E15=F67008 | F15=LZZ | G15=U | H15=UST |
              I15=712 | J15=8 | K15=8 | L15=65 | M15=7 | N15=60x40x40
              C19=APC | D19=PROD | E19=F67080 | F19=LAW | G19=U | H19=RT | I19=701 |
              J19=8 | K19=8 | L19=69 | M19=7 | N19=60x40x40

            ## Reglas

            1. ELIGE LA HOJA. Solo una hoja es el packing (lista de cajas: "LISTE DE
               COLIS", "LISTE DE COL", colisage...). Las facturas, descargas y hojas vacías
               se ignoran. Si VARIAS hojas son packings distintos (cada una de una fecha o
               un envío), convierte SOLO la primera, pon las demás en "hojasNoConvertidas"
               y dilo en "avisos": mezclar dos envíos sería peor que convertir uno. Si el
               mensaje te pide una hoja concreta, convierte esa y solo esa.

            2. UNA FILA DEL JSON POR CADA LÍNEA DE LA TABLA DEL TALLER, en el mismo orden.
               Una línea es un grupo de cajas seguidas con el mismo artículo, las mismas
               unidades por caja y la misma destinación. No fusiones líneas ni las partas.
               Lo TACHADO no existe: una fila tachada no se convierte.

               LA ÚNICA LÍNEA QUE SE PARTE es la que lleva DOS PEDIDOS a la vez. Cuando
               el taller junta en una misma caja mercancía de dos pedidos del cliente lo
               deja ver escribiendo los dos códigos juntos: "WHOLSEAL 930/948" es una
               caja de Wholesale con unidades del pedido 930 y del pedido 948; si cada
               código lleva delante su destinación, son dos pedidos de dos sitios. Esa
               línea se
               convierte en TANTAS FILAS SEGUIDAS como pedidos nombre, una por pedido,
               iguales en todo —las mismas "cajas", las mismas "unidadesPorCaja" (lo
               que lleva la caja entera, como lo escribe el taller)— salvo
               "codigoCliente" y, si también cambia, "destinacion". En "cantidad" va lo
               que la hoja diga de CADA pedido; si no lo dice, null en todas esas filas:
               no la repartas a partes iguales ni la calcules tú. NO pongas aviso por
               partirla: la aplicación reconoce esas filas, intenta repartir la caja
               con las cantidades del pedido del cliente y avisa ella.

            3. CELDAS VACÍAS QUE HEREDAN. Muchos talleres escriben el envío, el modelo, el
               color o la destinación solo en la primera fila de un bloque y dejan vacías
               las de debajo: esas filas llevan el valor de la de encima. Rellénalo.

            4. LOS CAMPOS:
               - "envio": el número de envío o expedición del taller ("ENVOI", "Envío").
                 Si esa columna está VACÍA pero otra hoja del libro (la factura,
                 "Facture") dice qué envío lleva cada artículo, pon ese número en las
                 filas de ese artículo y añade un aviso POR FILA con consecuencia
                 "envío tomado de la factura" diciendo de dónde sale: la aplicación
                 pinta esas filas para que se vea que no vienen del packing. Es el
                 ÚNICO dato que se toma de otra hoja. Si ninguna hoja lo dice, "" y
                 sin aviso.
               - "cliente": la clave del cliente de la fila (ver el bloque del cliente).
               - "motivo": PROD, SMS... solo si la hoja lo dice; si no, "".
               - "referencia", "color", "talla", "destinacion", "codigoCliente": ver el
                 bloque del cliente. Muchas veces vienen mezclados en una sola celda
                 ("SAC LAW GRIS WSALES" = tipo de artículo + color + destinación,
                 "Korea 690" = destinación + código de pedido): sepáralos. El tipo de
                 artículo ("SAC", "CEINTURE") no va a ningún campo.
               - "unidadesPorCaja": unidades en CADA caja de la línea ("Qtite/colis",
                 "Qté").
               - "cantidad": unidades TOTALES de la línea ("QTE T", la columna de total).
                 Si la línea no la trae, es unidadesPorCaja × número de cajas. Si la trae
                 y no cuadra con esa cuenta, copia la de la hoja y dilo en "avisos".
               - "cajas": los números de caja, "1-25" para un rango o "61" para una,
                 normalizando lo que escriba el taller ("1-2-3" → "1-3", "48--51" →
                 "48-51", "52--" → "52"). Son ORIENTATIVOS: la aplicación rehace el
                 packing con sus normas y no usa esta numeración. Si una línea no trae
                 número, déjalo "" y NO avises: no hace falta.
               - "pesoBrutoCaja" (kg, número con punto decimal) y "medidaCaja"
                 ("60x40x40"): solo si la hoja los da POR CAJA. Un peso total del envío
                 no es el peso de una caja: va a "notas". No los estimes nunca.

            5. COLUMNAS QUE NO SON DE NINGÚN CAMPO. Subtotales por modelo o por bloque
               (una columna que solo tiene número al final de cada bloque) no son
               cantidades de la línea: ignóralos para "cantidad", pero comprueba con ellos
               tus sumas y avisa si no cuadran.

            6. NOTAS. Todo lo que el taller escribe FUERA de la tabla principal y lleva
               información —tablas de cajas con defectos ("CAJA 27 | MAL"), bolsas o
               etiquetas que faltan, observaciones, totales de cajas, de peso bruto y
               neto— va a "notas": una entrada por fila, con sus celdas no vacías en
               orden, COPIADAS TAL CUAL (no traduzcas, no corrijas, no resumas). El
               membrete del taller va a "cabecera", y los títulos de columna no son notas.
               Estas notas solo se enseñan al usuario: nunca las conviertas en filas.
               Cada nota lleva además el color con que el taller la resalta, porque es
               parte del mensaje (en rojo van las cajas que están mal): en "letra" el
               #RRGGBB de [letra ...] y en "fondo" el de [fondo ...] de sus celdas, o ""
               si no lo llevan; esas marcas no se copian dentro de "celdas".
               Si entre las notas hay algo que quien prepara el envío tiene que mirar
               (cajas con defectos, cosas que faltan), añade UN SOLO aviso del fichero
               entero ("fila" null) que diga que está ahí y nada más: "Hay una tabla
               con cajas con problemas". No cuentes lo que dice ni cites sus filas: el
               contenido ya va copiado en "notas". Unos totales no necesitan aviso.

            7. CABECERA y RESUMEN. "cabecera": "taller" (nombre del taller, el membrete),
               "direccion", "factura" (el número, sin la palabra "Facture") y "fecha", si
               la hoja los trae; si no, "". "resumen": los totales que la hoja DECLARA
               ("1033", "Soit : 135 Colis"), copiados, no calculados por ti; null si no
               los declara. La aplicación los compara con la suma de tus filas.

            8. NUNCA INVENTES. Lo que no está en la hoja es "" en los textos y null en
               los números. Todo lo que no puedas
               leer con seguridad va igualmente con tu mejor lectura Y un aviso diciendo
               qué es dudoso y por qué. Cada aviso lleva en "fila" la posición (desde 1)
               de la fila convertida a la que se refiere, o null si habla del fichero
               entero; en "columna" el campo de la fila con la duda ("envio" para
               el envío tomado de la factura, "cajas" para una caja mixta, "color"
               para un color que no sabes traducir), porque la aplicación pinta ESA
               celda y pone ahí el comentario, no en la fila entera; y en
               "consecuencia" una etiqueta corta de su clase, la misma para todos
               los de esa clase: la aplicación los agrupa por ella y enseña el
               recuento. Los avisos van en español y los lee quien prepara el envío, no un
               programador: nada de "JSON" ni de nombres de campo; cita la fila del excel
               del taller ("fila 24 del excel") para que se encuentre.
               Los avisos son CORTOS: una frase con el hecho, sin explicar cómo has
               llegado a él, sin repetir lo que ya se ve en el excel y sin coletillas
               como "revisarlo antes de enviar". Mal: "Debajo de la tabla (filas 23 a 25
               del excel) el taller anota cajas con problemas: cajas 41-45 con la
               etiqueta mal, caja 62 con 10 mal y caja 43 con 5 mal. Revisarlo antes de
               enviar." Bien: "Hay una tabla con cajas con problemas".
               No avises de cómo has leído la hoja: qué columna has tomado para cada
               campo, que no traiga totales, membrete o pesos.

            8b. TODA CELDA INTERPRETADA LLEVA AVISO. La aplicación pinta de amarillo
               cada celda con aviso, y quien prepara el envío revisa a fondo
               justo esas: una celda interpretada sin aviso pasa por copia literal
               y nadie la mira. Interpretar es poner en una celda algo distinto de
               lo que escribe el taller: traducir un color ("Negro" → LZZ), elegir el
               más parecido ("Amarillo" → JAUNE CLAIR), completar una destinación
               ("CHINE" → CHINE FRANCH), corregir una errata ("WHOLSEAL",
               "G7008"), poner un dato que la hoja no da en esa fila. NO es
               interpretar, y no se avisa: separar una celda mezclada en sus partes,
               mayúsculas y espacios, normalizar los números de caja, una
               equivalencia que esté LITERALMENTE en la lista de destinaciones del
               bloque del cliente, ni heredar de la fila de encima un
               valor que no has cambiado.
               Lo interpretado se avisa en TODAS las filas donde está, también en
               las que lo heredan: si el taller escribe "AMARILLO" una vez y lo
               heredan las trece filas de debajo, son catorce celdas amarillas. Para
               no repetir el aviso, uno solo con "fila" en la primera y "hastaFila"
               en la última del tramo, siempre que la interpretación sea la misma en
               todas y las filas vayan seguidas; si no, un aviso por fila o por
               tramo. "hastaFila" null cuando es una sola fila.

            9. EL PEDIDO DEL CLIENTE. Si viene, es la lista de lo que el cliente ha pedido
               esta temporada, con su tabla de colores. Sirve para dos cosas y solo dos:
               - VALIDAR una lectura dudosa: un dígito que no se entiende ("G7008" donde
                 el resto del bloque dice "67008"), un color mal escrito. Corrige solo
                 cuando lo escrito casa con UNA sola entrada y dilo en "avisos" (qué
                 ponía, qué has puesto).
               - TRADUCIR un nombre de color a su código con la TABLA DE COLORES del
                 cliente (ver el bloque del cliente). El taller casi nunca escribe el
                 nombre como la tabla: lo pone en otro idioma o lo describe a su manera.
                 Si en la tabla hay un color que es el mismo, o claramente el más
                 parecido, usa el de la tabla. Solo si ninguno se le parece copia el
                 nombre tal cual: la aplicación avisará de que falta en la tabla.
               Lo que NO debes hacer con él: elegir o deducir el código de pedido, ni
               completar una referencia con su prefijo, ni cambiar un color porque "el
               pedido de ese código es en otro color". Eso lo cruza la aplicación después,
               fila a fila y con aviso; si lo haces tú se pierde el rastro. Si lo que
               escribe el taller no está en el pedido, transcríbelo tal cual: puede ser
               una reposición o una muestra. El pedido NO cambia nunca cantidades,
               unidades por caja ni números de caja.

            10. FILAS DE OTROS CLIENTES. Un taller trabaja para varios y manda una sola
                hoja. Las filas que no son del cliente elegido (por la referencia: AMI
                son ULL..., USL..., UBL...; APC son F63023, 67008, PXCEI-F67043...) se
                convierten también, con su "cliente", pero COPIANDO sus datos tal cual,
                sin normalizar ni avisar: la aplicación las aparta.
            """;

    static final String BLOQUE_AMI = """

            ## El cliente elegido es AMI

            - "cliente": "AMI" en sus filas.
            - "referencia": tal como la escribe el taller ("ULL163AL052"), en mayúsculas.
              NO la completes con puntos ni ceros: la aplicación la casa con el pedido.
              Si trae un tercer grupo ("UBL029.AL0216.2221"), ese grupo es el código de
              color y no es parte de la referencia.
            - "color": como lo escriba el taller. Si el pedido da ese color para esa
              referencia con código y nombre ("221 = DARK COFFEE"), escríbelo como
              "221 DARK COFFEE". Si el taller lo escribe en otro idioma ("MARRON",
              "NEGRO") y la referencia tiene en el pedido UN solo color que pueda ser
              ese, pon el del pedido y avisa; si tiene varios, tal cual y avisa.
            - "talla": "U" en bolsos y carteras; el número (75, 85, 95, 105) en
              cinturones.
            - "destinacion": CHINA, JAPAN o PARIS (PRESS para material de marketing).
              El taller abrevia: CH = CHINA, JA / JP / JAPON = JAPAN, PA / FR = PARIS.
              A veces la pega al final de la descripción ("SAC MARRON PA").
            - "codigoCliente": AMI no lo usa; "".
            """;

    static final String BLOQUE_APC = """

            ## El cliente elegido es APC

            - "cliente": "APC" en sus filas.
            - "referencia": como la escriba el taller ("67008", "F63023", "PXCEI-F67043"),
              en mayúsculas. NO la completes con el prefijo del pedido: lo hace la
              aplicación. Sí corrige un carácter que claramente es un error de lectura
              ("G7008" en un bloque que dice "67008"), con aviso. Y si la escribe con
              la piel, va SIEMPRE en el orden REF.PIEL-REF.ARTÍCULO, el del pedido
              ("PXCBC-F67008": la piel son solo letras, el artículo lleva cifras); el
              taller a veces la pone al revés: "F67008-PXCBC" se escribe "PXCBC-F67008",
              sin aviso.
            - "color": el CÓDIGO de color de APC (LZZ, LAW, GAU, CAB, KAN...). Si el
              taller escribe el código, ese ("LAW GRIS" → LAW). Si escribe un nombre,
              búscalo en la TABLA DE COLORES del pedido ("código = nombre") comparando
              el COLOR y no las letras: la tabla está en francés y el taller escribe
              en español, en inglés o a su manera.
                * Es el mismo color en otro idioma o con otra grafía: pon su código
                  y un aviso en "color" con consecuencia "color traducido". "Negro"
                  → LZZ si la tabla dice "LZZ = NOIR".
                * No hay uno igual pero sí UNO parecido, que solo añade o quita un
                  matiz: pon su código y un aviso en "color" con consecuencia "color
                  aproximado" que diga qué escribe el taller y qué color de la tabla
                  has puesto. "Amarillo" → el código de "JAUNE CLAIR" si es el único
                  amarillo de la tabla.
                En los dos casos el aviso cubre también las filas que heredan ese
                color (regla 8b).
                * Hay VARIOS igual de parecidos ("Amarillo" con "JAUNE CLAIR" y "JAUNE
                  MOUTARDE" en la tabla) o ninguno se parece ("CEREZA" sin ningún rojo):
                  copia el nombre tal cual. No elijas a ojo entre dos.
              Las palabras que nombran la PIEL o el material y no el color ("ALPACA",
              "CUIR", "LISSE", "GRAINÉ") no cuentan: "ALPACA AMARILLO" es AMARILLO.
              NUNCA deduzcas el color por el código de pedido ni por los colores en que
              el pedido tiene ese artículo: solo por el parecido del nombre.
            - "talla": "U" en bolsos; el número (75, 80, 85...) en cinturones.
            - "destinacion": el nombre de la destinación de APC, uno de estos:
                Wholesale / WSALES / WHO / WH -> WHOLESALE
                Australia -> AUSTRALIA
                China Franch / Chine French / Chine franch -> CHINE FRANCH
                Retail / RT -> RETAIL
                Wholesale concess -> WHOLESALE CONCESS
                Douanes USA / D. USA / USA / UST -> DOUANES USA
                Retail USA -> RETAIL USA
                Japan / Japon / JA / JPT -> JAPAN
                Korea / Corée / KRT -> KOREA
                Ivry -> IVRY
              Si la hoja dice una que no está en la lista pero se reconoce ("CHINE"
              → CHINE FRANCH, "WHOLSEAL" → WHOLESALE), pon la de la lista con un aviso
              en "destinacion" con consecuencia "destinación interpretada" (en todas
              sus filas, regla 8b); si no se reconoce, cópiala tal cual y avisa. Una
              línea con DOS códigos
              de pedido ("WHOLSEAL 930/948", "DOUANES USA 711/WHO 723") se parte en dos
              filas, una por código, como dice la regla 2.
            - "codigoCliente": el código de pedido de TRES cifras SOLO si el taller lo
              escribe, normalmente junto a la destinación ("Korea 690" → "690"). Cópialo
              exacto. Dos códigos en la misma celda ("930/948") son dos filas, cada una
              con el suyo (regla 2). Si el taller no lo escribe, "": lo resuelve la aplicación con el
              pedido. Si el pedido dice que ese código no existe para ese artículo, deja
              lo escrito y avisa.
            """;

    static final String BLOQUE_GENERICO = """

            ## El cliente elegido usa la plantilla genérica

            - "cliente": la clave del cliente elegido en sus filas.
            - "referencia" y "color" como los escribe el taller. "talla": "U" salvo que la
              hoja dé una talla. "destinacion": si la hoja la dice; si no, "".
              "codigoCliente": "".
            """;

    static final String MENSAJE_USUARIO = """
            Convierte el packing list de este excel de taller. Antes de responder, \
            comprueba línea a línea que la suma de "cantidad" cuadra con los totales que \
            trae la hoja (las filas de una caja con dos pedidos van sin cantidad y no \
            suman), que no has convertido en filas ninguna nota y que no has \
            inventado ningún dato.""";

    private final ObjectMapper mapper;
    private volatile AnthropicClient cliente;

    public ConversionFormatoTallerService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Convierte el excel del taller, lo ajusta contra el pedido y escribe el
     * excel en formato Puntotres. Lanza {@link ConversionException} con un
     * mensaje legible si el fichero no se puede leer, falta la clave de API,
     * la llamada falla o la respuesta no se entiende.
     *
     * @param hojaElegida hoja que el usuario ha pedido convertir, o null para
     *                    que el modelo elija
     */
    public Resultado convertir(byte[] excelTaller, String nombreFichero, String claveCliente,
                               PedidoParaConversion pedido, String hojaElegida) {
        String libro;
        try {
            libro = textoDelLibro(excelTaller, nombreFichero);
        } catch (IOException | RuntimeException e) {
            throw new ConversionException("No se ha podido abrir el excel del taller: "
                    + e.getMessage(), e);
        }
        IndiceCatalogoConversion.Indice indice = IndiceCatalogoConversion.para(pedido);

        Message respuesta;
        try {
            respuesta = acumular(peticionPara(libro, claveCliente, pedido.plantilla(),
                    indice.texto(), hojaElegida));
        } catch (AnthropicServiceException e) {
            throw new ConversionException("El servicio de IA ha devuelto un error: "
                    + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new ConversionException("No se pudo conectar con el servicio de IA: "
                    + e.getMessage(), e);
        }

        StopReason motivoParada = respuesta.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(motivoParada)) {
            throw new ConversionException("La IA ha rechazado procesar el excel del taller");
        }
        if (StopReason.MAX_TOKENS.equals(motivoParada)) {
            throw new ConversionException("La respuesta de la IA se ha cortado por longitud: "
                    + "el excel del taller es demasiado grande para convertirlo de una vez");
        }
        PackingFormatoPuntotres packing = parsear(textoDe(respuesta));
        if (packing.filas().isEmpty()) {
            throw new ConversionException("La IA no ha encontrado ninguna línea de packing en "
                    + "el excel del taller" + (packing.avisos().isEmpty() ? ""
                            : ": " + String.join(" ", packing.avisos().stream()
                                    .map(PackingFormatoPuntotres.Aviso::texto).toList())));
        }
        List<PackingFormatoPuntotres.Aviso> delPedido = new ArrayList<>();
        pedido.avisos().forEach(a -> delPedido.add(PackingFormatoPuntotres.Aviso.general(a)));
        indice.avisos().forEach(a -> delPedido.add(PackingFormatoPuntotres.Aviso.general(a)));
        packing = AjustadorConversion.ajustar(packing.conAvisosAnadidos(delPedido), pedido);

        Uso uso = new Uso(respuesta.usage().inputTokens(), respuesta.usage().outputTokens());
        return new Resultado(packing, FormatoPuntotresExcelBuilder.construir(packing), uso);
    }

    /**
     * La petición: índice del pedido (si hay), cliente, libro, hoja pedida y
     * mensaje de usuario, con el prompt del cliente y el esquema de salida.
     * Package-private para los tests.
     *
     * El índice va delante del libro y no en el prompt de sistema por lo
     * mismo que en la extracción por imágenes: el prompt es el de siempre y
     * el pedido cambia con la temporada.
     */
    static MessageCreateParams peticionPara(String libro, String claveCliente,
                                            TipoPlantilla plantilla, String indicePedido,
                                            String hojaElegida) {
        List<ContentBlockParam> bloques = new ArrayList<>();
        if (indicePedido != null && !indicePedido.isBlank()) {
            bloques.add(texto(indicePedido));
        }
        bloques.add(texto("Cliente elegido en la aplicación: " + claveCliente));
        bloques.add(texto(libro));
        if (hojaElegida != null && !hojaElegida.isBlank()) {
            bloques.add(texto("Convierte SOLO la hoja \"" + hojaElegida + "\" y ninguna otra."));
        }
        bloques.add(texto(MENSAJE_USUARIO));
        return MessageCreateParams.builder()
                .model(Model.of(MODELO))
                .maxTokens(TOKENS_MAXIMOS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(ESFUERZO)
                        .format(JsonOutputFormat.builder().schema(esquema()).build())
                        .build())
                .system(promptPara(plantilla))
                .addUserMessageOfBlockParams(bloques)
                .build();
    }

    static String promptPara(TipoPlantilla plantilla) {
        return PROMPT_NUCLEO + switch (plantilla) {
            case AMI -> BLOQUE_AMI;
            case APC -> BLOQUE_APC;
            case GENERIC -> BLOQUE_GENERICO;
        };
    }

    /** {@link #ESQUEMA} como lo pide el SDK. */
    static JsonOutputFormat.Schema esquema() {
        try {
            Map<String, Object> campos = new ObjectMapper().readValue(ESQUEMA,
                    new TypeReference<LinkedHashMap<String, Object>>() { });
            JsonOutputFormat.Schema.Builder constructor = JsonOutputFormat.Schema.builder();
            campos.forEach((clave, valor) -> constructor.putAdditionalProperty(clave,
                    JsonValue.from(valor)));
            return constructor.build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("El esquema de la conversión no es JSON válido", e);
        }
    }

    private static OutputConfig.Effort esfuerzoConfigurado() {
        String pedido = System.getProperty("conversion.esfuerzo");
        return pedido == null || pedido.isBlank() ? OutputConfig.Effort.HIGH
                : OutputConfig.Effort.of(pedido.trim().toLowerCase());
    }

    /** El libro del taller como texto ({@link VolcadoExcel}), con su nombre de fichero delante. */
    static String textoDelLibro(byte[] excel, String nombreFichero) throws IOException {
        String encabezado = "Excel del taller" + (nombreFichero != null && !nombreFichero.isBlank()
                ? " \"" + nombreFichero + "\"" : "") + ":";
        return VolcadoExcel.texto(excel, encabezado, MAXIMO_FILAS_POR_HOJA, MAXIMO_COLUMNAS);
    }

    private static ContentBlockParam texto(String contenido) {
        return ContentBlockParam.ofText(TextBlockParam.builder().text(contenido).build());
    }

    private Message acumular(MessageCreateParams peticion) {
        MessageAccumulator acumulador = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> flujo =
                     clienteApi().messages().createStreaming(peticion)) {
            flujo.stream().forEach(acumulador::accumulate);
        }
        return acumulador.message();
    }

    private AnthropicClient clienteApi() {
        AnthropicClient actual = cliente;
        if (actual != null) {
            return actual;
        }
        synchronized (this) {
            if (cliente == null) {
                try {
                    cliente = AnthropicOkHttpClient.builder()
                            .fromEnv()
                            .timeout(TIEMPO_MAXIMO)
                            .build();
                } catch (RuntimeException e) {
                    throw new ConversionException("Falta configurar la clave del servicio de IA "
                            + "(variable de entorno ANTHROPIC_API_KEY)", e);
                }
            }
            return cliente;
        }
    }

    private static String textoDe(Message respuesta) {
        StringBuilder texto = new StringBuilder();
        for (ContentBlock bloque : respuesta.content()) {
            bloque.text().ifPresent(t -> texto.append(t.text()));
        }
        return texto.toString();
    }

    /**
     * Convierte la respuesta. Con la salida estructurada viene ya como JSON
     * limpio; se sigue aislando el objeto por si algún día se quitara el
     * esquema. Package-private para los tests.
     */
    PackingFormatoPuntotres parsear(String texto) {
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) {
            throw new ConversionException("La respuesta de la IA no contiene un JSON");
        }
        try {
            return mapper.readValue(texto.substring(inicio, fin + 1), PackingFormatoPuntotres.class);
        } catch (JsonProcessingException e) {
            throw new ConversionException("La respuesta de la IA no se entiende: "
                    + e.getOriginalMessage(), e);
        }
    }
}
