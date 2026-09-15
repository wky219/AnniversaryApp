Add-Type -AssemblyName System.Drawing

# 源图文件名为中文，避免脚本编码问题，用通配符定位
$src = (Get-ChildItem -Path $PSScriptRoot -Filter '*.png' | Select-Object -First 1).FullName
$res = Join-Path $PSScriptRoot '..\app\src\main\res'
# 源图为 8 位索引色 PNG，GDI+ 直接绘制会失败，先转成 32 位 ARGB
$indexed = [System.Drawing.Image]::FromFile($src)
$source = New-Object System.Drawing.Bitmap $indexed.Width, $indexed.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$sg = [System.Drawing.Graphics]::FromImage($source)
$sg.DrawImage($indexed, 0, 0, $indexed.Width, $indexed.Height)
$sg.Dispose()
$indexed.Dispose()

$densities = @{ mdpi = 1.0; hdpi = 1.5; xhdpi = 2.0; xxhdpi = 3.0; xxxhdpi = 4.0 }

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    $g.Clear([System.Drawing.Color]::Transparent)
    return @($bmp, $g)
}

function Save-Png($bmp, $path) {
    $dir = Split-Path $path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

foreach ($d in $densities.Keys) {
    $scale = $densities[$d]
    $dir = Join-Path $res "mipmap-$d"

    # 自适应图标前景：108dp 画布，图案缩放到约 79%，保证花环落在 66dp 安全区内
    $fgSize = [int](108 * $scale)
    $content = [int]($fgSize * 0.79)
    $offset = [int](($fgSize - $content) / 2)
    $bmp, $g = New-Canvas $fgSize
    $g.DrawImage($source, $offset, $offset, $content, $content)
    $g.Dispose()
    Save-Png $bmp (Join-Path $dir 'ic_launcher_foreground.png')

    # 传统方形图标：48dp，白底圆角
    $size = [int](48 * $scale)
    $bmp, $g = New-Canvas $size
    $radius = [int]($size * 0.18)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, $radius * 2, $radius * 2, 180, 90)
    $path.AddArc($size - $radius * 2, 0, $radius * 2, $radius * 2, 270, 90)
    $path.AddArc($size - $radius * 2, $size - $radius * 2, $radius * 2, $radius * 2, 0, 90)
    $path.AddArc(0, $size - $radius * 2, $radius * 2, $radius * 2, 90, 90)
    $path.CloseFigure()
    $g.SetClip($path)
    $g.Clear([System.Drawing.Color]::White)
    $g.DrawImage($source, 0, 0, $size, $size)
    $g.Dispose()
    Save-Png $bmp (Join-Path $dir 'ic_launcher.png')

    # 传统圆形图标：48dp，白底圆形裁切，图案略缩以免花环贴边
    $bmp, $g = New-Canvas $size
    $circle = New-Object System.Drawing.Drawing2D.GraphicsPath
    $circle.AddEllipse(0, 0, $size, $size)
    $g.SetClip($circle)
    $g.Clear([System.Drawing.Color]::White)
    $inner = [int]($size * 0.92)
    $pad = [int](($size - $inner) / 2)
    $g.DrawImage($source, $pad, $pad, $inner, $inner)
    $g.Dispose()
    Save-Png $bmp (Join-Path $dir 'ic_launcher_round.png')
}

$source.Dispose()
Write-Output 'done'
