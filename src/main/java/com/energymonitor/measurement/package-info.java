/**
 * Measurement service: the Monitoring and Measurements bounded context of EnergyMonitor, deployed
 * on its own.
 *
 * <p>Owns the readings the measuring modules (ESP32 + PZEM-004T) publish, the consumption history
 * and statistics computed from them and the catalogue of consumption levels. Homes are asked to
 * energy-monitor-backend and devices to the device service, by identifier only.
 *
 * <p>Internal layout: {@code domain}, {@code application}, {@code adapter},
 * {@code infrastructure}.
 */
package com.energymonitor.measurement;
