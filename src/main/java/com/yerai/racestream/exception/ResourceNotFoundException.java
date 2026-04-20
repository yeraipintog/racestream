/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Excepción para recursos no encontrados
 */
package com.yerai.racestream.exception;

public class ResourceNotFoundException extends RuntimeException {
    /**
     * @description Constructor de ResourceNotFoundException
     * @param message Mensaje de error
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
