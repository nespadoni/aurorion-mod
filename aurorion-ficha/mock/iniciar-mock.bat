@echo off
rem Mock local do site Aurorion: duplo clique sobe o mock e abre o painel no navegador.
chcp 65001 >nul
cd /d "%~dp0"
title Aurorion - mock do site
where python >nul 2>nul
if not errorlevel 1 goto python
where py >nul 2>nul
if not errorlevel 1 goto py
echo Python 3 nao encontrado. Instale o Python 3.12 em https://www.python.org/downloads/ e marque "Add to PATH".
goto fim

:python
python -X utf8 mock_site.py --abrir %*
goto fim

:py
py -3 -X utf8 mock_site.py --abrir %*

:fim
echo.
echo O mock parou. Pressione uma tecla para fechar.
pause >nul
