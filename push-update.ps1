$updates_dir = "/data/lineageos_update"
$zip_path = $args[0]
$zip_name = [io.path]::GetFileName($zip_path)
$zip_hash = Get-FileHash $zip_path
$id = $zip_hash."Hash"
$version = Write-Output $zip_name | ForEach-Object{($_ -split "-")[1]}
$build_date = Write-Output $zip_name | ForEach-Object{($_ -split "-")[2]} | ForEach-Object{($_ -split "_")[0]}
$type = Write-Output $zip_name | ForEach-Object{($_ -split "-")[3]}
$dateobject = [datetime]::parseexact($build_date, 'yyyyMMdd', $null)
$timestamp = Get-Date $dateobject -UFormat "%s"
$zip_stat = Get-Item -Path $zip_path
$size = $zip_stat."Length"

$zip_path_device = "$updates_dir/$zip_name"


adb push $zip_path $zip_path_device
adb shell chgrp cache $zip_path_device
adb shell chmod 664 $zip_path_device
adb shell "killall org.lineageos.updater 2>/dev/null"

Write-Output "Run the below commands manually."
Write-Output
Write-Output "adb shell    #this will give you a root shell on the device"
Write-Output "sqlite3 /data/data/org.lineageos.updater/databases/updates.db"
Write-Output "INSERT INTO updates (status, path, download_id, timestamp, type, version, size) VALUES (1, '$zip_path_device', '$id', $timestamp, '$type', '$version', $size)"
Write-Output
Write-Output "Continue following instructions in your device's wiki page"