package com.energymonitor.measurement.adapter.in.web;

import com.energymonitor.measurement.application.exception.ConsumptionLevelNotFoundException;
import com.energymonitor.measurement.application.exception.DeviceServiceUnavailableException;
import com.energymonitor.measurement.application.exception.HomeNotAccessibleException;
import com.energymonitor.measurement.application.exception.HomeServiceUnavailableException;
import com.energymonitor.measurement.application.exception.MeasurementNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler for the measurement module's web layer.
 *
 * <p>Maps application exceptions to HTTP status codes:
 * <ul>
 *   <li>{@link MeasurementNotFoundException} → 404 Not Found</li>
 *   <li>{@link ConsumptionLevelNotFoundException} → 404 Not Found</li>
 *   <li>{@link HomeServiceUnavailableException}, {@link DeviceServiceUnavailableException} → 503
 *       Service Unavailable: the service owning the home or the device did not answer</li>
 *   <li>{@link IllegalArgumentException} → 400 Bad Request</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "com.energymonitor.measurement.adapter.in.web")
public class MeasurementExceptionHandler {

    @ExceptionHandler(MeasurementNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMeasurementNotFound(MeasurementNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HomeNotAccessibleException.class)
    public ResponseEntity<Map<String, String>> handleHomeNotAccessible(HomeNotAccessibleException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ConsumptionLevelNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleLevelNotFound(ConsumptionLevelNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({HomeServiceUnavailableException.class, DeviceServiceUnavailableException.class})
    public ResponseEntity<Map<String, String>> handleUnavailable(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "the consumption data cannot be checked right now, try again later"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message));
    }
}
