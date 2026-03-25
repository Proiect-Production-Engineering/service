package ro.unibuc.prodeng.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.response.BalanceSheetEntry;
import ro.unibuc.prodeng.response.BalanceSheetResponse;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final TransactionRepository transactionRepository;
    private final BankAccountService bankAccountService;

    public BalanceSheetResponse getBalanceSheet(String accountId) {
        return getBalanceSheet(accountId, null, null);
    }

    public BalanceSheetResponse getBalanceSheet(String accountId, Instant from, Instant to) {
        BankAccountEntity account = bankAccountService.getEntityById(accountId);

        List<TransactionEntity> transactions;
        if (from != null && to != null) {
            transactions = transactionRepository.findByAccountIdAndTimestampBetweenOrderByTimestampAsc(accountId, from, to);
        } else {
            transactions = transactionRepository.findByAccountIdOrderByTimestampAsc(accountId);
        }

        List<BalanceSheetEntry> entries = computeRunningBalance(transactions);

        BigDecimal currentBalance = entries.isEmpty()
                ? BigDecimal.ZERO
                : entries.getLast().runningBalance();

        return new BalanceSheetResponse(
                account.getId(),
                account.getAccountHolderName(),
                account.getCurrencyCode(),
                currentBalance,
                entries
        );
    }

    List<BalanceSheetEntry> computeRunningBalance(List<TransactionEntity> transactions) {
        List<BalanceSheetEntry> entries = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;

        for (TransactionEntity tx : transactions) {
            if (tx.type() == TransactionType.CREDIT) {
                runningBalance = runningBalance.add(tx.amount());
            } else {
                runningBalance = runningBalance.subtract(tx.amount());
            }

            entries.add(new BalanceSheetEntry(
                    tx.id(),
                    tx.timestamp(),
                    tx.description(),
                    tx.type().name(),
                    tx.amount(),
                    runningBalance
            ));
        }

        return entries;
    }
}
