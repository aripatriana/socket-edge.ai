package id.co.jalin.seconsole.engine.model;

/**
 * Mirrors the engine's {@code /healthcheck} payload.
 *
 * @param status "OK" (active) or "STANDBY" (slave / paused)
 * @param role   "MASTER" or "SLAVE"
 * @param mode   "CLUSTER" or "STANDALONE"
 */
public record EngineHealth(String status, String role, String mode) {}
