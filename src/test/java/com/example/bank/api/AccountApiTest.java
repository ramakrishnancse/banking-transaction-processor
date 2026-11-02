package com.example.bank.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AccountApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void opening_an_account_returns_its_number_and_location() throws Exception {
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Alice\",\"initialDeposit\":100.00}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.accountNumber").isNumber())
                .andExpect(jsonPath("$.ownerName").value("Alice"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void blank_owner_is_a_bad_request() throws Exception {
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\" \",\"initialDeposit\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void balance_can_be_queried() throws Exception {
        long a = createAccount("Alice", "42.00");

        mvc.perform(get("/api/v1/accounts/" + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(42.00));
    }

    @Test
    void deposit_and_withdrawal_update_balance_and_history_newest_first() throws Exception {
        long a = createAccount("Alice", "10.00");

        postDeposit(a, "5.50", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(15.50));
        postWithdrawal(a, "0.50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(15.00));

        mvc.perform(get("/api/v1/accounts/" + a + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.items[2].type").value("OPENING"));
    }

    @Test
    void history_is_paged() throws Exception {
        long a = createAccount("Alice", "10.00");
        postDeposit(a, "1", null);
        postDeposit(a, "2", null);

        mvc.perform(get("/api/v1/accounts/" + a + "/transactions?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void overdraft_is_unprocessable() throws Exception {
        long a = createAccount("Alice", "10.00");

        postWithdrawal(a, "10.01").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalid_amounts_are_bad_requests() throws Exception {
        long a = createAccount("Alice", "10.00");

        postDeposit(a, "-1", null).andExpect(status().isBadRequest());
        postDeposit(a, "1.001", null).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/accounts/" + a + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknown_account_is_not_found() throws Exception {
        mvc.perform(get("/api/v1/accounts/999")).andExpect(status().isNotFound());
    }

    @Test
    void non_numeric_account_number_is_a_bad_request() throws Exception {
        mvc.perform(get("/api/v1/accounts/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void retrying_a_deposit_with_the_same_idempotency_key_applies_it_once() throws Exception {
        long a = createAccount("Alice", "10.00");

        postDeposit(a, "5.00", "pay-1").andExpect(status().isOk());
        postDeposit(a, "5.00", "pay-1").andExpect(status().isOk());

        mvc.perform(get("/api/v1/accounts/" + a)).andExpect(jsonPath("$.balance").value(15.00));
    }

    @Test
    void reusing_an_idempotency_key_for_a_different_amount_is_a_conflict() throws Exception {
        long a = createAccount("Alice", "10.00");

        postDeposit(a, "5.00", "pay-2").andExpect(status().isOk());
        postDeposit(a, "6.00", "pay-2").andExpect(status().isConflict());
    }

    @Test
    void transfer_moves_money_and_returns_the_senders_receipt() throws Exception {
        long a = createAccount("Alice", "100.00");
        long b = createAccount("Bob", "0.00");

        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":" + a + ",\"toAccount\":" + b + ",\"amount\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.counterpartyAccountNumber").value(b))
                .andExpect(jsonPath("$.balanceAfter").value(70.00));

        mvc.perform(get("/api/v1/accounts/" + b)).andExpect(jsonPath("$.balance").value(30.00));
    }

    @Test
    void transfer_to_unknown_account_is_not_found() throws Exception {
        long a = createAccount("Alice", "100.00");

        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":" + a + ",\"toAccount\":999,\"amount\":30}"))
                .andExpect(status().isNotFound());
    }

    // Helper names must not be post/get: they would hide the statically imported request builders.

    private long createAccount(String owner, String initialDeposit) throws Exception {
        String body = mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"" + owner + "\",\"initialDeposit\":" + initialDeposit + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.accountNumber")).longValue();
    }

    private ResultActions postDeposit(long account, String amount, String idempotencyKey) throws Exception {
        var request = post("/api/v1/accounts/" + account + "/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    private ResultActions postWithdrawal(long account, String amount) throws Exception {
        return mvc.perform(post("/api/v1/accounts/" + account + "/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }
}
