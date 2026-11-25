@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo === Ametra: creando el instalador animado ===

echo [1/4] Instalando dependencias del launcher...
call npm install
if errorlevel 1 ( echo ERROR en npm install & pause & exit /b 1 )

echo [2/4] Empaquetando el launcher...
call npm run build
if errorlevel 1 ( echo ERROR en npm run build & pause & exit /b 1 )

if not exist "installer\payload" mkdir "installer\payload"
copy /y "dist\AmetraClient Setup 1.3.0.exe" "installer\payload\setup.exe" >nul
if errorlevel 1 ( echo ERROR: no se encontro dist\AmetraClient Setup 1.3.0.exe & pause & exit /b 1 )

echo [3/4] Calculando tamano para la barra de progreso...
powershell -NoProfile -Command "$b=(Get-ChildItem 'dist\win-unpacked' -Recurse -File | Measure-Object Length -Sum).Sum; Set-Content -Path 'installer\payload-info.json' -Value ('{\"bytes\":'+$b+'}') -Encoding ASCII"

echo [4/4] Creando el instalador animado...
cd installer
call npm install
if errorlevel 1 ( echo ERROR en npm install del instalador & pause & exit /b 1 )
call npm run build
if errorlevel 1 ( echo ERROR al crear el instalador & pause & exit /b 1 )

echo.
echo LISTO: installer\dist\AmetraClient-Instalador.exe
pause
