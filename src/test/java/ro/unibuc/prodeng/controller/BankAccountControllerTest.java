package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ro.unibuc.prodeng.request.CreateTransferRequest;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.BankAccountService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class BankAccountControllerTest {

    @Mock
    private BankAccountService bankAccountService;

    @InjectMocks
    private BankAccountController bankAccountController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bankAccountController)
                .setControllerAdvice(new ro.unibuc.prodeng.exception.GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testTransferEndpoint_returnsCreatedWithTransactions() throws Exception {
        // Arrange
        TransactionResponse debit = new TransactionResponse(
                "tx-1",
                "acc-1",
                "DEBIT",
                new BigDecimal("100.00"),
                "Transfer",
                Instant.now()
        );

        TransactionResponse credit = new TransactionResponse(
                "tx-2",
                "acc-2",
                "CREDIT",
                new BigDecimal("100.00"),
                "Transfer",
                Instant.now()
        );

        when(bankAccountService.transfer(any(CreateTransferRequest.class)))
                .thenReturn(List.of(debit, credit));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("100.00"),
                "Transfer"
        );

        // Act & Assert
        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].accountId").value("acc-1"))
                .andExpect(jsonPath("$[0].type").value("DEBIT"))
                .andExpect(jsonPath("$[1].accountId").value("acc-2"))
                .andExpect(jsonPath("$[1].type").value("CREDIT"));
    }
}
