package com.energymonitor.measurement.infrastructure;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the OpenAPI description and the one security scheme this API authenticates with: a
 * bearer JWT signed by the security service.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Energy Monitor Measurement API",
        version = "v1",
        description = "Readings of the measuring devices: history, latest reading and statistics "
                + "of a device, consumption of a home and the consumption levels. The bearer "
                + "token is issued by the security service."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiSecurityConfig {
}
