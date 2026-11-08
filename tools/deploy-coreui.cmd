@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-coreui.ps1" %*
pause
