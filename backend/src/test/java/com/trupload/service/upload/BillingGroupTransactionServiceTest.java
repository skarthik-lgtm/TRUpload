package com.trupload.service.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class BillingGroupTransactionServiceTest {

    @Test
    void insertsAllRoutesAndUpdatesGroupToSuccessWithinTransactionalMethod() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        BillingGroupTransactionService service = new BillingGroupTransactionService(jdbcTemplate);
        List<BillingGroupTransactionService.RouteToUpload> routes = List.of(
                new BillingGroupTransactionService.RouteToUpload("112345", LocalDate.of(2024, 1, 1), false),
                new BillingGroupTransactionService.RouteToUpload("212346", LocalDate.of(2024, 1, 2), false));

        BillingGroupTransactionService.GroupInsertResult result = service.insertSuccessfulGroup(
                45L, "CSTRAB", "0101T01", "user@example.com", false, routes);

        assertEquals("SUCCESS", result.status());
        assertEquals(2, result.uploadedCount());
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(
                contains("INSERT INTO [TRUpload].[dbo].[UploadedBilling]"), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        verify(jdbcTemplate).update(contains("UPDATE [TRUpload].[dbo].[UploadGroupResult]"),
                eq("SUCCESS"), eq(2), eq(2), anyString(), eq(45L));

        Method method = BillingGroupTransactionService.class.getMethod(
                "insertSuccessfulGroup", long.class, String.class, String.class,
                String.class, boolean.class, List.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals("sqlServerTransactionManager", transactional.transactionManager());
        assertEquals(1, transactional.rollbackFor().length);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }
}
