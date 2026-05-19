/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 09-03-2026
 * @modified 18-05-2026
 * @description Punto de arranque principal de RaceStream
 */
package com.yerai.racestream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RacestreamApplication {
	/**
	 * @author Yerai Pinto
	 * @since 1.0
	 * @version 1.0.1
	 * @created 09-03-2026
	 * @modified 18-05-2026
	 * @description Arranca la aplicacion Spring Boot de RaceStream
	 * @param args Argumentos de la linea de comandos
	 */
	public static void main(String[] args) {
		SpringApplication.run(RacestreamApplication.class, args);
	}
}
