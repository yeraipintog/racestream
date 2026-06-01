/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 01-06-2026
 * @modified 01-06-2026
 * @description Configura automáticamente RaceStream para Railway convirtiendo las URL MySQL de Railway al formato JDBC necesario para Spring Boot.
 */
package com.yerai.racestream.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class RailwayEnvironmentBootstrap {

    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    private RailwayEnvironmentBootstrap() {
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 01-06-2026
     * @modified 01-06-2026
     * @description Aplica la configuración necesaria antes de arrancar Spring Boot en Railway.
     */
    public static void configure() {
        configureServerPort();
        configureMysqlDatasource();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 01-06-2026
     * @modified 01-06-2026
     * @description Configura el puerto HTTP usando la variable PORT de Railway.
     */
    private static void configureServerPort() {
        String port = System.getenv("PORT");

        if (hasText(port)) {
            System.setProperty("server.port", port);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 01-06-2026
     * @modified 01-06-2026
     * @description Configura MySQL usando una única URL de Railway y la transforma al formato JDBC.
     */
    private static void configureMysqlDatasource() {
        String mysqlUrl = firstNonBlank(
                System.getenv("RAILWAY_MYSQL_URL"),
                System.getenv("MYSQL_PUBLIC_URL"),
                System.getenv("MYSQL_URL")
        );

        if (!hasText(mysqlUrl)) {
            return;
        }

        MysqlConnectionData data = parseMysqlUrl(mysqlUrl);

        System.setProperty("spring.datasource.url", data.jdbcUrl());
        System.setProperty("spring.datasource.username", data.username());
        System.setProperty("spring.datasource.password", data.password());
        System.setProperty("spring.datasource.driver-class-name", MYSQL_DRIVER);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 01-06-2026
     * @modified 01-06-2026
     * @description Convierte una URL mysql:// de Railway en una URL jdbc:mysql:// compatible con Spring Boot.
     * @param mysqlUrl URL MySQL original de Railway.
     * @return Datos de conexión JDBC.
     */
    private static MysqlConnectionData parseMysqlUrl(String mysqlUrl) {
        URI uri = URI.create(mysqlUrl);

        String userInfo = uri.getUserInfo();

        if (!hasText(userInfo) || !userInfo.contains(":")) {
            throw new IllegalStateException("La URL MySQL de Railway no contiene usuario y contraseña válidos.");
        }

        String[] credentials = userInfo.split(":", 2);

        String username = decode(credentials[0]);
        String password = decode(credentials[1]);
        String database = uri.getPath().replaceFirst("^/", "");

        if (!hasText(database)) {
            throw new IllegalStateException("La URL MySQL de Railway no contiene nombre de base de datos.");
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 3306;

        String jdbcUrl = "jdbc:mysql://" + uri.getHost() + ":" + port + "/" + database
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC"
                + "&connectTimeout=30000"
                + "&socketTimeout=60000";

        return new MysqlConnectionData(jdbcUrl, username, password);
    }

    private static String firstNonBlank(String first, String second, String third) {
        if (hasText(first)) {
            return first;
        }

        if (hasText(second)) {
            return second;
        }

        return third;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record MysqlConnectionData(String jdbcUrl, String username, String password) {
    }
}