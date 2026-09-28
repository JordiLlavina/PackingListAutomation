# Convierte fotos HEIC a JPEG reducido con el decodificador de Windows (WIC).
# Lo lanza ConversorHeicWindows. Cada línea de la lista es "entrada<TAB>salida"
# y por cada una se escribe "OK<TAB>n" o "ERR<TAB>n<TAB>mensaje", con n el
# número de línea: los caminos no vuelven por la salida para no depender de
# la página de códigos de la consola.
param(
    [Parameter(Mandatory = $true)][string]$lista,
    [int]$max = 1600,
    [int]$calidad = 88
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore
$n = 0
foreach ($linea in [IO.File]::ReadAllLines($lista, [Text.Encoding]::UTF8)) {
    $n++
    if ([string]::IsNullOrWhiteSpace($linea)) { continue }
    $partes = $linea.Split("`t")
    try {
        $decodificador = [System.Windows.Media.Imaging.BitmapDecoder]::Create(
            [Uri]$partes[0], 'IgnoreColorProfile', 'OnLoad')
        $marco = $decodificador.Frames[0]
        $escala = [Math]::Min(1.0, $max / [Math]::Max($marco.PixelWidth, $marco.PixelHeight))
        $fuente = $marco
        if ($escala -lt 1.0) {
            $fuente = New-Object System.Windows.Media.Imaging.TransformedBitmap(
                $marco, (New-Object System.Windows.Media.ScaleTransform($escala, $escala)))
        }
        $codificador = New-Object System.Windows.Media.Imaging.JpegBitmapEncoder
        $codificador.QualityLevel = $calidad
        $codificador.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($fuente))
        $salida = [IO.File]::Create($partes[1])
        try { $codificador.Save($salida) } finally { $salida.Close() }
        [Console]::Out.WriteLine("OK`t$n")
    } catch {
        $mensaje = $_.Exception.Message -replace "[`r`n`t]", ' '
        [Console]::Out.WriteLine("ERR`t$n`t$mensaje")
    }
    [Console]::Out.Flush()
}
