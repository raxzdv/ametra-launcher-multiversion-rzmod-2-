@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
cd /d "%~dp0"
rem Las herramientas se guardan fuera de Descargas (Windows/antivirus suelen bloquear ahi: "Acceso denegado")
set "TOOLS=%LOCALAPPDATA%\AmetraBuild"
if not exist "%TOOLS%" mkdir "%TOOLS%"

echo === Ametra: compilando el mod (la primera vez tarda varios minutos) ===

rem --- JDK: usa el del sistema si ya es 21 o mas; si no, el descargado ---
set "HAVEJDK="
if exist "%TOOLS%\jdk\bin\javac.exe" set "HAVEJDK=1"
if not defined HAVEJDK (
  where javac >nul 2>&1 && (
    javac -version 2>&1 | findstr /r /c:"javac 2[1-9]" >nul && set "SYSJDK=1"
  )
)
if defined SYSJDK ( echo [1/3] Usando el JDK instalado en tu PC. & goto :gradle )
if defined HAVEJDK goto :gradle

echo [1/3] Descargando JDK 21...
curl.exe -L --fail -o "%TOOLS%\jdk.zip" "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
if errorlevel 1 goto :jdkfail
if exist "%TOOLS%\jdk-tmp" rmdir /s /q "%TOOLS%\jdk-tmp"
mkdir "%TOOLS%\jdk-tmp"
tar -xf "%TOOLS%\jdk.zip" -C "%TOOLS%\jdk-tmp"
if errorlevel 1 goto :jdkfail
if exist "%TOOLS%\jdk" rmdir /s /q "%TOOLS%\jdk"
for /d %%D in ("%TOOLS%\jdk-tmp\*") do move "%%D" "%TOOLS%\jdk" >nul
rmdir /s /q "%TOOLS%\jdk-tmp" 2>nul
del "%TOOLS%\jdk.zip" 2>nul
if exist "%TOOLS%\jdk\bin\javac.exe" goto :gradle

:jdkfail
echo.
echo ERROR: no se pudo instalar el JDK automaticamente.
echo Solucion rapida: abre PowerShell y ejecuta:
echo    winget install EclipseAdoptium.Temurin.21.JDK
echo (o descargalo en https://adoptium.net, version 21). Cierra esta ventana, abre otra y vuelve a ejecutar compilar-mod.bat
echo Si sigue diciendo "Acceso denegado": desactiva un momento el antivirus / "Acceso controlado a carpetas" de Windows Defender.
pause
exit /b 1

:gradle
if not exist "%TOOLS%\gradle\bin\gradle.bat" (
  echo [2/3] Descargando Gradle...
  curl.exe -L --fail -o "%TOOLS%\gradle.zip" "https://services.gradle.org/distributions/gradle-8.12-bin.zip"
  if errorlevel 1 goto :gradlefail
  if exist "%TOOLS%\gradle-tmp" rmdir /s /q "%TOOLS%\gradle-tmp"
  mkdir "%TOOLS%\gradle-tmp"
  tar -xf "%TOOLS%\gradle.zip" -C "%TOOLS%\gradle-tmp"
  if errorlevel 1 goto :gradlefail
  if exist "%TOOLS%\gradle" rmdir /s /q "%TOOLS%\gradle"
  for /d %%D in ("%TOOLS%\gradle-tmp\*") do move "%%D" "%TOOLS%\gradle" >nul
  rmdir /s /q "%TOOLS%\gradle-tmp" 2>nul
  del "%TOOLS%\gradle.zip" 2>nul
)
if not exist "%TOOLS%\gradle\bin\gradle.bat" goto :gradlefail
goto :build

:gradlefail
echo ERROR: no se pudo instalar Gradle. Revisa tu internet / antivirus y vuelve a intentarlo.
pause
exit /b 1

:build
if exist "%TOOLS%\jdk\bin\javac.exe" set "JAVA_HOME=%TOOLS%\jdk"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
set "PATH=%TOOLS%\gradle\bin;%PATH%"

echo [3/3] Compilando...
cd ametra-mod
call gradle build --no-daemon
if errorlevel 1 (
  echo.
  echo ERROR al compilar. Copia el texto rojo de arriba y mandaselo a Claude.
  pause
  exit /b 1
)

if not exist "..\mod" mkdir "..\mod"
for %%F in (build\libs\rzmod-*.jar) do (
  echo %%F | findstr /i "sources" >nul || copy /y "%%F" "..\mod\rzmod.jar" >nul
)
if exist "..\mod\rzmod.jar" (
  echo.
  echo LISTO: mod\rzmod.jar creado. Abre el launcher, juega 1.21.1 y pulsa Shift derecho.
) else (
  echo ERROR: no se encontro el .jar compilado.
)
pause
