# RaceStream

RaceStream es una aplicación web de Fórmula 1 desarrollada como TFG por Yerai Pinto. Su objetivo es reunir calendario, sesiones, clasificaciones, directo, favoritos, noticias, cuenta de usuario y ayuda contextual en una interfaz clara, visual y fácil de entender incluso para usuarios sin conocimientos previos de F1.

## Resumen Del Proyecto

- Aplicación full-stack con Spring Boot, MySQL y frontend estático en HTML, CSS y JavaScript.
- Experiencia visual común en todas las páginas mediante navbar, franja de próximo GP y footer reutilizados.
- Datos deportivos integrados desde OpenF1, Jolpica y F1DB, con caché defensiva para evitar mostrar resultados vacíos como definitivos.
- Gestión de usuarios con registro local, inicio de sesión JSON, sesiones técnicas, preferencias, favoritos y roles.
- Páginas públicas de información, calendario, sesiones, clasificaciones, pilotos, equipos, noticias, ayuda, FAQ y páginas legales.
- Páginas privadas para cuenta, favoritos, preferencias, privacidad, foro y contacto.
- Zona de administración protegida por rol `ADMIN`.

## Tecnologías

- Java 17.
- Spring Boot 3.
- Spring Security.
- Spring Data JPA e Hibernate.
- MySQL en ejecución normal.
- H2 en pruebas automatizadas.
- HTML, CSS y JavaScript sin frameworks frontend.
- Maven Wrapper para ejecutar el proyecto y los tests.

## Fuentes De Datos

- OpenF1: sesiones, directo, clima, control de carrera y datos operativos.
- Jolpica: calendario oficial, resultados y clasificaciones.
- F1DB: fuente de datos históricos utilizada para enriquecer información de circuitos, temporadas y Grandes Premios.
- BBDD propia: usuarios, favoritos, preferencias, foro, contacto, cookies y datos internos.
- GNews: noticias externas complementarias cuando la clave esté configurada.

## Funcionalidades Principales

- Inicio con resumen visual de la temporada y acceso rápido a secciones clave.
- Calendario con próximos Grandes Premios, sesiones y horarios.
- Sesiones con explicación para usuarios no expertos.
- Clasificaciones de pilotos y constructores.
- Páginas de pilotos, equipos, noticias y directo.
- Favoritos de usuario para seguir contenido relevante.
- Cuenta privada con preferencias, privacidad y notificaciones.
- Foro y contacto protegidos para usuarios autenticados.
- Páginas legales: términos, privacidad y cookies.

## Autenticación Y Seguridad

RaceStream usa un login propio mediante `/api/auth/login`; no depende del formulario genérico de Spring Security. Las páginas privadas redirigen a `/login.html` y las APIs privadas devuelven error JSON `401` cuando falta autenticación.

Rutas principales:

- `/login.html`: acceso y registro visual de RaceStream.
- `/login`: redirección corta a `/login.html`.
- `/register`: redirección corta a `/login.html#registro`.
- `/api/auth/me`: estado de sesión actual.
- `/api/auth/logout`: cierre de sesión.

## Cookies

RaceStream usa la cookie técnica `rs_cookie_consent` para recordar la decisión del usuario. No se usan cookies analíticas ni publicitarias desde la aplicación.

- Si se aceptan cookies, se guarda `rs_cookie_consent=accepted`, se oculta el banner y, si el usuario está autenticado, se guarda el estado `ACCEPTED` en BBDD.
- Si se rechazan cookies, se guarda `rs_cookie_consent=rejected`, se oculta el banner y, si el usuario está autenticado, se guarda el estado `REJECTED` en BBDD.
- Si no se ha decidido todavía, no debe existir `rs_cookie_consent` y el estado del usuario es `UNDECIDED`; el banner se muestra.
- Al iniciar sesión sin cookie local, `/api/auth/me` permite restaurar `accepted` o `rejected` desde BBDD. Si el estado es `UNDECIDED`, el banner permanece visible.
- La decisión se puede cambiar desde `/cookies.html`.

Pruebas manuales recomendadas de cookies:

1. Abrir la web sin `rs_cookie_consent` y comprobar que aparece el banner.
2. Aceptar cookies y verificar en DevTools que existe `rs_cookie_consent=accepted`.
3. Borrar la cookie local, iniciar sesión con un usuario que haya aceptado y comprobar que se restaura `accepted`.
4. Rechazar cookies y verificar `rs_cookie_consent=rejected`.
5. Borrar la cookie local, iniciar sesión con un usuario que haya rechazado y comprobar que se restaura `rejected` sin mostrar el banner.
6. Registrar un usuario nuevo y comprobar que `/api/auth/me` devuelve `cookieConsentStatus=UNDECIDED`.
7. Cambiar la decisión desde `/cookies.html` y comprobar que la cookie cambia.

## Estructura

- `src/main/java/com/yerai/racestream/config`: configuración de seguridad, contraseñas, filtros y arranque de admin.
- `src/main/java/com/yerai/racestream/controller`: APIs REST y redirecciones de páginas.
- `src/main/java/com/yerai/racestream/model`: entidades JPA y enums.
- `src/main/java/com/yerai/racestream/repository`: repositorios Spring Data.
- `src/main/java/com/yerai/racestream/service`: integración con fuentes externas y lógica de dominio.
- `src/main/resources/static`: páginas HTML, estilos CSS, JavaScript y assets.
- `src/test/java`: tests unitarios e integración.

## Ejecución Local

1. Preparar una base de datos MySQL para RaceStream.
2. Configurar las propiedades o variables necesarias en el entorno local.
3. Ejecutar la aplicación:

```bash
./mvnw spring-boot:run
```

En Windows también se puede usar:

```bat
mvnw.cmd spring-boot:run
```

## Tests

Ejecutar toda la suite:

```bash
./mvnw test
```

En Windows:

```bat
mvnw.cmd test
```

## Validación Manual Recomendada

1. Entrar en `/` y comprobar que no aparece el login genérico de Spring.
2. Abrir `/login` y verificar que redirige a `/login.html`.
3. Abrir `/account.html` sin sesión y verificar que redirige a `/login.html`.
4. Probar registro e inicio de sesión con usuario local.
5. Revisar en móvil navbar, franja de próximo GP, menú, cookies y formularios.
6. Comprobar calendario, sesiones, clasificaciones, noticias, favoritos y páginas legales.
7. Ejecutar `mvnw.cmd test` o `./mvnw test` antes de entregar cambios.

## Autor

Yerai Pinto González
