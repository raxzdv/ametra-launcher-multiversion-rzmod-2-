@echo off
chcp 65001 >nul
title Tunel de cuentas Ametra
cd /d "%~dp0"
where node >nul 2>nul || (echo Falta Node.js: instalalo desde https://nodejs.org y vuelve a abrir esto. & pause & exit /b)
where cloudflared >nul 2>nul || (
  echo Falta cloudflared. Instalalo con esta orden y vuelve a abrir esto:
  echo    winget install --id Cloudflare.cloudflared
  pause & exit /b
)
echo Arrancando el servidor de cuentas en otra ventana...
start "Servidor de cuentas Ametra" cmd /k "set TRUST_PROXY=1&& node server.js"
timeout /t 2 >nul
echo.
echo ===== Abajo saldra una direccion https://algo.trycloudflare.com =====
echo Copiala y pegala en accounts-url.json (en tu GitHub) para que el launcher la use.
echo Deja esta ventana y la del servidor abiertas mientras jueguen.
echo.
cloudflared tunnel --url http://127.0.0.1:8787
pause
