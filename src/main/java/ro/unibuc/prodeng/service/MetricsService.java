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
 *   <li>{@code app_user_registrations_total} (Counter, business)</li>
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

        this.userCreatedCounter = Counter.builder("app_user_registrations_total")
                .description("Total number of users registered in the system")
                .tag("type", "business")
                .register(registry);

        // NOTE: We deliberately do NOT call publishPercentiles(...) here.
        // publishPercentiles(...) makes Micrometer emit the meter as a Prometheus
        // *summary* with client-side {quantile="..."} series and no _bucket
        // series. The Grafana panel "Transfer latency (p50/p95/p99)" uses
        //     histogram_quantile(..., rate(app_transfer_duration_seconds_bucket[5m]))
        // which requires histogram buckets to exist. publishPercentileHistogram(true)
        // alone causes Micrometer to emit a real Prometheus histogram with
        // _bucket series, which is what the dashboard expects (and is also
        // correctly aggregatable across instances).
        this.transferTimer = Timer.builder("app_transfer_duration_seconds")
                .description("Time taken to execute a money transfer between bank accounts")
                .publishPercentileHistogram(true)
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

    public void recordTransferTimer(Long duration) {
        transferTimer.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
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
