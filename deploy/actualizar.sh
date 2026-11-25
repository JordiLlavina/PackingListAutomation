#!/usr/bin/env bash
# Actualiza la aplicación de packing list en el servidor Linux (apartado 8 de
# INSTALACION-SERVIDOR.md): para el servicio, copia la base de datos, cambia el
# jar y vuelve a arrancar.
#
# Uso:  sudo ./actualizar.sh [ruta-del-jar-nuevo]
#   Sin argumento, coge /tmp/packing-list-ami-1.0.0-SNAPSHOT.jar (donde lo deja
#   el scp del apartado 3).
#
# Si cualquier paso falla, el script se detiene ahí y no sigue a ciegas.
set -euo pipefail

JAR_NUEVO="${1:-/tmp/packing-list-ami-1.0.0-SNAPSHOT.jar}"
SERVICIO="packinglist"
CARPETA="/opt/packinglist"
JAR="$CARPETA/packinglist.jar"
DATOS="$CARPETA/datos"
COPIA="$CARPETA/datos-copia-$(date +%F)"

if [[ "$(id -u)" -ne 0 ]]; then
    echo "Hay que ejecutarlo con sudo: sudo $0 ${1:-}" >&2
    exit 1
fi

if [[ ! -f "$JAR_NUEVO" ]]; then
    echo "No existe el jar nuevo: $JAR_NUEVO" >&2
    echo "Cópialo antes con scp (apartado 3) o pasa la ruta como argumento." >&2
    exit 1
fi

echo "==> Parando el servicio $SERVICIO"
systemctl stop "$SERVICIO"

if [[ -d "$DATOS" ]]; then
    if [[ -e "$COPIA" ]]; then
        # Segunda actualización el mismo día: no pisar la copia de la mañana.
        COPIA="$COPIA-$(date +%H%M%S)"
    fi
    echo "==> Copiando la base de datos a $COPIA"
    cp -r "$DATOS" "$COPIA"
else
    echo "==> No hay carpeta $DATOS (primer arranque): no hay nada que copiar"
fi

if [[ -f "$JAR" ]]; then
    echo "==> Guardando el jar actual como $JAR.anterior (por si hay que volver atrás)"
    cp "$JAR" "$JAR.anterior"
fi

echo "==> Instalando $JAR_NUEVO como $JAR"
cp "$JAR_NUEVO" "$JAR"
chown packinglist:packinglist "$JAR"

echo "==> Arrancando el servicio $SERVICIO"
systemctl start "$SERVICIO"

echo
echo "Hecho. Comprueba que arranca sin errores con:"
echo "    sudo journalctl -u $SERVICIO -f"
echo "Para volver atrás: parar el servicio, restaurar $JAR.anterior sobre $JAR"
echo "y la carpeta $COPIA sobre $DATOS, y arrancar."
