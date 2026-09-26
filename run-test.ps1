# Run Maven test using the JVM directly
$javaExe = "C:\Users\omita\.jdks\openjdk-22.0.2\bin\java.exe"
$mavenHome = "C:\Users\omita\Downloads\maven-mvnd-1.0.5-windows-amd64\maven-mvnd-1.0.5-windows-amd64\mvn"
$classworlds = "$mavenHome\boot\plexus-classworlds-2.9.0.jar"
$projectDir = "C:\Users\omita\.bob\playground\backend"
$outFile = "C:\Users\omita\.bob\playground\mvn-out.txt"

$args = @(
    "-Xms256m",
    "-Xmx1g",
    "-classpath", $classworlds,
    "-Dclassworlds.conf=$mavenHome\bin\m2.conf",
    "-Dmaven.home=$mavenHome",
    "-Dmaven.multiModuleProjectDirectory=$projectDir",
    "org.codehaus.plexus.classworlds.launcher.Launcher",
    "test",
    "-Dtest=GitServiceTest",
    "-B",
    "--no-transfer-progress"
)

$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = $javaExe
$pinfo.Arguments = ($args | ForEach-Object { if ($_ -match '\s') { "`"$_`"" } else { $_ } }) -join ' '
$pinfo.WorkingDirectory = $projectDir
$pinfo.RedirectStandardOutput = $true
$pinfo.RedirectStandardError = $true
$pinfo.UseShellExecute = $false
$pinfo.CreateNoWindow = $true

$p = New-Object System.Diagnostics.Process
$p.StartInfo = $pinfo
$p.Start() | Out-Null

$stdout = $p.StandardOutput.ReadToEnd()
$stderr = $p.StandardError.ReadToEnd()
$p.WaitForExit(200000)

"=== STDOUT ===" | Out-File $outFile -Encoding utf8
$stdout | Out-File $outFile -Encoding utf8 -Append
"=== STDERR ===" | Out-File $outFile -Encoding utf8 -Append
$stderr | Out-File $outFile -Encoding utf8 -Append
"EXIT_CODE=$($p.ExitCode)" | Out-File $outFile -Encoding utf8 -Append
