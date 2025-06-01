@echo off
REM Renombrar 'calc 1.py' a 'calc.py'
ren "calc 1.py" "calc.py"

REM Establecer la variable de entorno FLASK_APP a 'api'
set FLASK_APP=api

REM Ejecutar 'flask run'
flask run
