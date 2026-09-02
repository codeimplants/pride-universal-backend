package com.prideuniversal.domain.settings;

import com.prideuniversal.common.ApiException;
import com.prideuniversal.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Read access to per-wing billing configuration, cached.
 *
 * <h2>Why the cache holds a record and not the entity</h2>
 * {@link #ratesFor} returns a {@link BillingRates} snapshot rather than the
 * {@link WingSettings} entity. Caching a JPA entity hands the same managed
 * instance to callers in different transactions, after the persistence context
 * that loaded it has closed — a detached object that will throw on any lazy
 * access and can be mutated by one caller for everyone. An immutable record
 * has no identity, no session and nothing to initialise, so it is safe to hold
 * for as long as the cache wants it.
 *
 * <p>Bill generation reads these once per wing and multiplies them across
 * every flat, which is the read-heavy, write-rare shape a cache is for.
 */
@Service
public class WingSettingsService {

    /** The numbers a billing run actually needs. */
    public record BillingRates(
            String wing,
            BigDecimal monthlyTotal,
            BigDecimal lateFee,
            int dueDay
    ) {}

    private final WingSettingsRepository repository;

    public WingSettingsService(WingSettingsRepository repository) {
        this.repository = repository;
    }

    /**
     * Billing rates for a wing.
     *
     * <p>Throws rather than defaulting to zero when a wing has no settings
     * row. Silently billing everyone ₹0 would look like the feature working.
     *
     * <p>Note that Spring caches only successful returns, so the exception
     * path is never cached — an admin can add the missing row and retry
     * immediately.
     */
    @Cacheable(cacheNames = CacheConfig.WING_SETTINGS, key = "#wing")
    @Transactional(readOnly = true)
    public BillingRates ratesFor(String wing) {
        WingSettings settings = repository.findByWing(wing)
                .orElseThrow(() -> ApiException.badRequest(
                        "Wing " + wing + " has no billing settings yet. Set the monthly "
                                + "maintenance amount before generating bills."));

        // A missing row is not the only way to have no amount configured.
        // V1__initial_schema.sql seeds a wing_settings row per wing with every
        // amount defaulted to 0, so the committee has something to edit rather
        // than something to create. That means "row exists" proves nothing —
        // without this check, generation would happily raise a bill for zero
        // rupees against every flat and report success.
        if (settings.monthlyTotal().signum() <= 0) {
            throw ApiException.badRequest(
                    "Wing " + wing + " has a maintenance amount of zero. Set the monthly "
                            + "maintenance, sinking fund or water charges before generating bills.");
        }

        return new BillingRates(
                settings.getWing(),
                settings.monthlyTotal(),
                settings.getLateFee(),
                settings.getMaintenanceDueDay());
    }

    /** Call after any write to a wing's settings, or bills use stale amounts. */
    @CacheEvict(cacheNames = CacheConfig.WING_SETTINGS, key = "#wing")
    public void evict(String wing) {
        // The annotation is the whole implementation.
    }
}
