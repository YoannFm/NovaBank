package dev.novadev.novabank.config.model;

/**
 * Section {@code diagnostics}.
 *
 * @param debug         messages de debogage dans la console
 * @param logOperations journalisation de chaque depot / retrait dans la console
 */
public record DiagnosticsSettings(boolean debug, boolean logOperations) {
}
