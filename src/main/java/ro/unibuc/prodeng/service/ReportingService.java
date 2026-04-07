package ro.unibuc.prodeng.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.UserDetails;
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
        return getBalanceSheet(account, from, to);
    }

    public BalanceSheetResponse getAuthorizedBalanceSheet(String accountId, Instant from, Instant to) {
        BankAccountEntity account = bankAccountService.getEntityById(accountId);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !account.getUserId().equals(userDetails.getId())) {
            throw new AccessDeniedException("You do not have access to this account's balance sheet");
        }

        return getBalanceSheet(account, from, to);
    }

    public BalanceSheetResponse getBalanceSheet(BankAccountEntity account, Instant from, Instant to) {
        String accountId = account.getId();

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
