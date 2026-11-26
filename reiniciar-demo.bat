@echo off
rem Rehace la base de demostración desde cero y arranca la aplicación con los datos de hoy.
rem Úsalo antes de cada práctica y unos 30 minutos antes de exponer: las citas de demostración se crean
rem relativas al día y la hora en que se corre (pacientes en espera, cupo liberado, cola de confirmación).
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo  Esto BORRA la base citas-pg (todo lo que se haya hecho en ella) y la crea de nuevo
echo  con los datos de demostración de hoy. Antes, cierra la aplicación si está abierta (Ctrl + C).
echo.
choice /C SN /M "¿Seguir"
if errorlevel 2 exit /b 0

docker info >nul 2>&1
if errorlevel 1 (
    echo.
    echo  Docker no responde. Abre Docker Desktop, espera a que diga "Engine running" y vuelve a intentarlo.
    pause
    exit /b 1
)

echo  Borrando la base anterior...
docker rm -f citas-pg >nul 2>&1
echo  Creando la base nueva en el puerto 5434...
docker run -d --name citas-pg -p 5434:5432 -e POSTGRES_DB=citas_medicas -e POSTGRES_USER=citas -e POSTGRES_PASSWORD=citas postgres:16-alpine >nul
if errorlevel 1 (
    echo  No se pudo crear la base. Revisa que el puerto 5434 esté libre.
    pause
    exit /b 1
)

echo  Esperando a que la base esté lista...
:espera
timeout /t 2 /nobreak >nul
docker exec citas-pg pg_isready -U citas -d citas_medicas >nul 2>&1
if errorlevel 1 goto espera
rem PostgreSQL se reinicia una vez al terminar de crearse: se espera un poco más y se vuelve a comprobar.
timeout /t 3 /nobreak >nul
docker exec citas-pg pg_isready -U citas -d citas_medicas >nul 2>&1
if errorlevel 1 goto espera

echo  Arrancando la aplicación (perfil dev) en http://localhost:18080 ...
echo  Está lista cuando la consola dice "Started CitasMedicasApplication". Para detenerla: Ctrl + C.
set DB_URL=jdbc:postgresql://localhost:5434/citas_medicas
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=18080
