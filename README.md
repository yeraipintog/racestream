# RaceStream

RaceStream es una aplicación web de Fórmula 1 desarrollada como TFG por Yerai Pinto González. Reúne calendario, sesiones, clasificaciones, directo, pilotos, escuderías, favoritos, noticias, cuenta de usuario y ayuda contextual en una interfaz visual, clara y pensada para usuarios que no tienen por qué conocer la Fórmula 1.

## Resumen del proyecto

- Aplicación full-stack con Spring Boot, MySQL y frontend estático en HTML, CSS y JavaScript.
- Navbar, franja de próximo GP, cabecera visual y footer coherentes en todas las páginas.
- Datos deportivos integrados desde OpenF1, Jolpica y F1DB, con caché, reintentos y protección frente a respuestas vacías temporales.
- Registro local, inicio de sesión JSON, sesiones con Spring Security, cookies técnicas, favoritos, notificaciones y roles.
- Temporada actual visible para usuarios invitados; filtros históricos desbloqueados solo al iniciar sesión.
- Zona En Vivo, cuenta, favoritos, preferencias, privacidad, foro, contacto y administración protegidas.
- Panel de administración con usuario demo para la defensa del proyecto.

## Tecnologías

- Java 17.
- Spring Boot 3.5.
- Spring Security.
- Spring Data JPA e Hibernate.
- MySQL en ejecución normal.
- H2 en pruebas automatizadas.
- Flyway para migraciones.
- HTML, CSS y JavaScript sin framework frontend.
- Maven Wrapper para ejecución y tests.

## Fuentes de datos

- OpenF1 Premium: fuente principal para meetings, sesiones, directo, clima, control de carrera, telemetría, posiciones, vueltas, adelantamientos, radio de equipo y resultados de sesión disponibles.
- Jolpica: clasificaciones, resultados oficiales, calendario histórico y respaldo cuando OpenF1 no cubre una temporada o sesión concreta.
- F1DB: enriquecimiento de circuitos, datos históricos y Grandes Premios.
- Base de datos propia: usuarios, favoritos, preferencias, foro, contacto, cookies, notificaciones y administración.
- GNews: noticias externas complementarias cuando la clave esté configurada.

La integración con OpenF1 centraliza autenticación, token, caché, reintentos y rate limit en backend. Jolpica y F1DB se usan como apoyo para completar datos históricos sin sustituir a OpenF1 cuando OpenF1 ofrece datos válidos.

## Funcionalidades principales

- Inicio con resumen visual de la temporada y accesos rápidos.
- Calendario por temporada con Grandes Premios, horarios y sesiones.
- Sesiones completadas con resultados, podios, vueltas y explicaciones visuales.
- Clasificaciones de pilotos y constructores con detalle por Gran Premio.
- Páginas de pilotos y escuderías con tarjetas por temporada.
- En Vivo privado con mapa, timing, carrera, datos recientes y estados de sesión.
- Favoritos y recordatorios para usuarios autenticados.
- Noticias externas filtradas por temática F1.
- Cuenta privada con perfil, contraseña, preferencias, privacidad y notificaciones.
- Foro, contacto y panel de administración protegidos.
- Páginas legales: términos, privacidad y cookies.

## Reglas de acceso

- Invitado:
  - Puede ver páginas públicas y datos de la temporada actual.
  - No puede entrar en En Vivo.
  - No puede usar filtros históricos de temporada.
  - No puede usar favoritos, foro, contacto, cuenta, preferencias ni administración.
- Usuario autenticado:
  - Puede usar filtros históricos.
  - Puede gestionar favoritos, cuenta, privacidad, preferencias, foro y contacto.
  - Puede acceder a En Vivo.
- Administrador:
  - Puede entrar en `/admin.html`.
  - Puede revisar usuarios, mensajes, correos bloqueados y publicaciones.

## Rutas principales

- `/index.html`: inicio.
- `/calendar.html`: calendario.
- `/sessions.html`: sesiones.
- `/standings.html`: clasificaciones.
- `/drivers.html`: pilotos.
- `/teams.html`: escuderías.
- `/news.html`: noticias.
- `/live.html`, `/live-timing.html`, `/live-race.html`: En Vivo privado.
- `/login.html`: acceso, registro y recuperación de contraseña.
- `/account.html`: cuenta.
- `/favorites.html`: favoritos.
- `/forum.html`: foro.
- `/contact.html`: contacto.
- `/admin.html`: administración.

## Configuración local

El proyecto está pensado para presentarse con `src/main/resources/application.properties`.

1. Crear la base de datos MySQL `racestream_db`.
2. Copiar `src/main/resources/application-example.properties` como `src/main/resources/application.properties`.
3. Completar las credenciales locales de MySQL, OpenF1, GNews y correo si se quieren probar esas funciones.
4. Mantener `src/test/resources/application.properties` para tests con H2.

Propiedades importantes:

- `openf1.api.base-url`
- `openf1.auth.username`
- `openf1.auth.password`
- `openf1.auth.access-token`
- `openf1.stream.enabled`
- `jolpica.api.base-url`
- `f1db.splitted-json-url`
- `gnews.api.key`
- `racestream.contact.mail-enabled`
- `racestream.password-reset.mail-enabled`
- `spring.mail.*`

## Ejecución local

Windows:

```bat
mvnw.cmd spring-boot:run
```

Linux o macOS:

```bash
./mvnw spring-boot:run
```

La aplicación queda disponible en:

```text
http://localhost:8080
```

## Tests

Windows:

```bat
mvnw.cmd test
```

Linux o macOS:

```bash
./mvnw test
```

La suite usa H2 en memoria y no necesita MySQL real.

## Cookies

RaceStream usa la cookie técnica `rs_cookie_consent` para recordar la decisión del usuario. No se usan cookies analíticas ni publicitarias desde la aplicación.

- `accepted`: cookies técnicas aceptadas.
- `rejected`: cookies técnicas rechazadas.
- `UNDECIDED`: estado inicial en base de datos cuando el usuario todavía no ha elegido.

La decisión puede cambiarse desde `/cookies.html`.

## Estructura del proyecto

- `src/main/java/com/yerai/racestream/config`: seguridad, filtros, contraseñas, RestTemplate y usuario admin.
- `src/main/java/com/yerai/racestream/controller`: APIs REST y redirecciones.
- `src/main/java/com/yerai/racestream/model`: entidades JPA y enums.
- `src/main/java/com/yerai/racestream/repository`: repositorios Spring Data.
- `src/main/java/com/yerai/racestream/service`: integraciones externas y lógica de dominio.
- `src/main/resources/db/migration`: migraciones Flyway.
- `src/main/resources/static`: HTML, CSS, JavaScript y assets.
- `src/test/java`: tests unitarios e integración.
- `src/test/resources`: configuración de tests.

## Estado esperado para la defensa

Antes de presentar:

1. Ejecutar `mvnw.cmd test`.
2. Ejecutar `mvnw.cmd spring-boot:run`.
3. Verificar páginas públicas en escritorio y móvil.
4. Verificar que En Vivo redirige al login sin sesión.
5. Verificar que los filtros históricos quedan bloqueados sin sesión.
6. Iniciar sesión y comprobar filtros históricos, favoritos y cuenta.
7. Entrar como `admin` y revisar administración.

## Autor

Yerai Pinto González
