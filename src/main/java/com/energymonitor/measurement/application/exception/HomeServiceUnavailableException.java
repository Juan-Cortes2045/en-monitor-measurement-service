package com.energymonitor.measurement.application.exception;

/**
 * Thrown when the home service, owner of the homes, does not answer whether the caller
 * belongs to a home or what its limits are. Nothing is shown without that answer: the request is
 * refused as unavailable (503).
 */
public class HomeServiceUnavailableException extends MeasurementApplicationException {

    public HomeServiceUnavailableException(String message) {
        super(message);
    }

    public HomeServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
