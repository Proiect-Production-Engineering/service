package ro.unibuc.prodeng.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.repository.BankAccountRepository;

/**
 * Centralised registry for SafeTransfer's custom Micrometer metrics.
 *
 * Five metrics are exposed under the {@code app_*} namespace, one per category
 * required by Lab 8 (business, performance, error, resource, domain-specific):
 * <ul>
 *   <li>{@code app_users_created_total} (Counter, business)</li>
 *   <li>{@code app_transfer_duration_seconds} (Timer, performance)</li>
 *   <li>{@code app_errors_total} (Counter with {@code exception} tag, error)</li>
 *   <li>{@code app_active_bank_accounts} (Gauge, resource)</li>
 *   <li>{@code app_transfers_total} (Counter with {@code status} tag, domain-specific)</li>
 * </ul>
 */
@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final Counter userCreatedCounter;
    private final Timer transferTimer;

    public MetricsService(MeterRegistry registry, BankAccountRepository bankAccountRepository) {
        this.registry = registry;

        this.userCreatedCounter = Counter.builder("app_users_created_total")
                .description("Total number of users registered in the system")
                .tag("type", "business")
                .register(registry);

        this.transferTimer = Timer.builder("app_transfer_duration_seconds")
                .description("Time taken to execute a money transfer between bank accounts")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Resource gauge: number of currently active (non-deleted) bank accounts.
        // The supplier is invoked on every scrape, so values stay current without
        // any explicit increment/decrement bookkeeping.
        Gauge.builder("app_active_bank_accounts", bankAccountRepository,
                        repo -> (double) repo.countByDeletedFalse())
                .description("Number of bank accounts that are currently open (not soft-deleted)")
                .register(registry);
    }

    public void recordUserCreated() {
        userCreatedCounter.increment();
    }

    public Timer getTransferTimer() {
        return transferTimer;
    }

    public void recordTransfer(String status) {
        Counter.builder("app_transfers_total")
                .description("Total number of attempted money transfers, tagged by outcome")
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public void recordError(String exceptionType) {
        Counter.builder("app_errors_total")
                .description("Total number of application errors handled by the global exception handler")
                .tag("exception", exceptionType)
                .register(registry)
                .increment();
    }
}
