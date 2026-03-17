package ro.unibuc.prodeng.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.model.UserDetails;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.request.CreateTransferRequest;
import ro.unibuc.prodeng.response.TransactionResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class BankAccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BankAccountService bankAccountService;

    private static final String CURRENT_USER_ID = "user-1";

    @BeforeEach
    void setUpSecurityContext() {
        UserDetails principal = UserDetails.builder()
                .id(CURRENT_USER_ID)
                .username("john")
                .email("john@example.com")
                .password("secret")
                .authorities(List.of())
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

        private BankAccountEntity makeAccount(String id, String userId, String currency, boolean deleted, double balance) {
        BankAccountEntity account = new BankAccountEntity();
        account.setId(id);
        account.setUserId(userId);
        account.setCurrencyCode(currency);
        account.setDeleted(deleted);
                account.setBalance(balance);
        return account;
    }

    private TransactionEntity makeTransaction(String id, String accountId, TransactionType type, BigDecimal amount) {
        return new TransactionEntity(id, accountId, type, amount, "desc", Instant.now());
    }
}
