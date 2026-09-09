package gg.lightpractice.integration.vault;

import java.util.UUID;

/**
 * Economy bridge used by cosmetics that cost real money instead of coins.
 *
 * <p>Implemented through reflection on Vault, so the plugin keeps working when Vault or an economy
 * provider is missing: {@link #available()} simply returns false.</p>
 */
public interface VaultEconomy {

    boolean available();

    /** Name of the backing plugin, for example the economy provider, used in debug output. */
    String provider();

    double balance(UUID uuid);

    boolean has(UUID uuid, double amount);

    boolean withdraw(UUID uuid, double amount);

    boolean deposit(UUID uuid, double amount);

    /** Formats an amount the way the economy provider does. */
    String format(double amount);
}
