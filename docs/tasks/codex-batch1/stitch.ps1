param([ValidateSet('tabs', 'gold', 'island', 'mana')][string]$Frame = 'tabs')

Add-Type -AssemblyName System.Drawing
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$raw = Join-Path $root 'app/build/codex-batch1'
$labels = @()
$captions = @()
$top = 0
$height = 680
$secondTop = $null

switch ($Frame) {
    'tabs' {
        $labels = @('0 · До', 'A · По слову', 'B · По вкладке', 'C · Пилюля', 'D · Точка')
        $captions = @('Жирное слово без индикатора', 'Основные вкладки M3', 'Индикатор во всю вкладку', 'Фон под выбранным словом', 'Небольшая отметка снизу')
    }
    'gold' {
        $labels = @('A · Античное золото', 'B · Латунь', 'C · Шампань', 'D · Бронза', 'E · Охра', 'F · Оливковое золото')
        $captions = Get-Content -Encoding UTF8 (Join-Path $raw 'gold-captions.txt')
        $top = 774
        $height = 150
    }
    'island' {
        $labels = @('0 · Нейтральный', 'A · Яркий M3', 'B · Инверсный', 'C · Античное золото', 'D · Латунь')
        $captions = @('Покой сверху, запись снизу', 'Основной тональный цвет M3', 'Тёмная панель, светлые значки', 'Глубокий тёплый тон', 'Приглушённый светлый металл')
        $top = 774
        $height = 150
        $secondTop = 774
    }
    'mana' {
        $labels = @('0 · До', 'A · Мана', 'B · Риска нормы', 'C · Надсечка', 'D · В бюджете', 'E · Ниже нуля')
        $captions = @('Цветом показано потраченное', 'Заполнение показывает остаток', 'Норма остатка на сегодня', 'Отметка над полосой', 'Темп ниже планового', 'Дневной остаток отрицательный')
        $top = 8
        $height = 260
    }
}

$width = 432
$gap = 14
$header = 42
$footer = 70
$rows = if ($null -eq $secondTop) { 1 } else { 2 }
$bitmap = [Drawing.Bitmap]::new(($width + $gap) * $labels.Count + $gap, $header + $height * $rows + $gap * ($rows - 1) + $footer)
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.Clear([Drawing.Color]::White)
$graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$bold = [Drawing.Font]::new('Arial', 22, [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Pixel)
$normal = [Drawing.Font]::new('Arial', 18, [Drawing.FontStyle]::Regular, [Drawing.GraphicsUnit]::Pixel)
for ($i = 0; $i -lt $labels.Count; $i++) {
    $x = $gap + $i * ($width + $gap)
    $graphics.DrawString($labels[$i], $bold, [Drawing.Brushes]::Black, $x, 9)
    $index = if ($Frame -eq 'gold') { $i + 1 } else { $i }
    $suffix = if ($Frame -eq 'island') { '_rest' } else { '' }
    $source = [Drawing.Image]::FromFile((Join-Path $raw "${Frame}_${index}${suffix}.png"))
    $graphics.DrawImage($source, [Drawing.Rectangle]::new($x, $header, $width, $height), [Drawing.Rectangle]::new(0, $top, $width, $height), [Drawing.GraphicsUnit]::Pixel)
    $source.Dispose()
    if ($null -ne $secondTop) {
        $source = [Drawing.Image]::FromFile((Join-Path $raw "${Frame}_${index}_record.png"))
        $graphics.DrawImage($source, [Drawing.Rectangle]::new($x, $header + $height + $gap, $width, $height), [Drawing.Rectangle]::new(0, $secondTop, $width, $height), [Drawing.GraphicsUnit]::Pixel)
        $source.Dispose()
    }
    $graphics.DrawString($captions[$i], $normal, [Drawing.Brushes]::DimGray, [Drawing.RectangleF]::new($x, $header + $height * $rows + $gap * ($rows - 1) + 10, $width, $footer - 10))
}
$bitmap.Save((Join-Path $PSScriptRoot "$Frame.png"), [Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()
$bold.Dispose()
$normal.Dispose()
