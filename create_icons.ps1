$pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
$bytes = [Convert]::FromBase64String($pngBase64)

foreach ($density in @('mdpi', 'hdpi', 'xhdpi', 'xxhdpi')) {
    $path = "c:\Users\sriha\Desktop\Applications\BYSEL\BYSEL\android\app\src\main\res\mipmap-$density\ic_launcher.png"
    [IO.File]::WriteAllBytes($path, $bytes)
    Write-Host "Created: mipmap-$density/ic_launcher.png"
}
