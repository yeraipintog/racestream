/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 09-03-2026
 * @modified 18-05-2026
 * @description Punto de arranque principal de RaceStream con tareas programadas de notificaciones
 */
package com.yerai.racestream;

import com.yerai.racestream.config.RailwayEnvironmentBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RacestreamApplication {
	/**
	 * @author Yerai Pinto
	 * @since 1.0
	 * @version 1.1.0
	 * @created 09-03-2026
	 * @modified 18-05-2026
	 * @description Arranca la aplicación Spring Boot de RaceStream
	 * @param args Argumentos de la línea de comandos
	 */
	public static void main(String[] args) {
		RailwayEnvironmentBootstrap.configure();
		SpringApplication.run(RacestreamApplication.class, args);
	}
}
