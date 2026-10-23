package com.trupload.service.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;

import com.trupload.api.upload.UploadRequest;
import com.trupload.api.upload.UploadSelection;
import com.trupload.service.database.DatabaseInstanceService;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class BillingUploadServiceTest {

        @Mock
        private JdbcTemplate jdbcTemplate;

        @Mock
        private BillingGroupTransactionService transactionService;

        @Mock
        private DatabaseInstanceService databaseInstanceService;

        private BillingUploadService service;

        @BeforeEach
        void setUp() {
                service = new BillingUploadService(jdbcTemplate, transactionService, databaseInstanceService);
                lenient().when(jdbcTemplate.queryForObject(
                                contains("SELECT CASE WHEN EXISTS"), eq(Integer.class),
                                any(), any(), any()))
                                .thenReturn(0);
        }

        @Test
        void rejectsNullRequestAndEmptySelectionsBeforeDatabaseWrites() {
                assertThrows(ResponseStatusException.class, () -> service.upload(null, "user@example.com", "USER"));
                assertThrows(ResponseStatusException.class,
                                () -> service.upload(new UploadRequest(List.of()), "user@example.com", "USER"));
                verifyNoInteractions(jdbcTemplate, transactionService, databaseInstanceService);
        }

        @Test
        void rejectsMissingUsernameAndUnsupportedRoleBeforeDatabaseWrites() {
                UploadRequest request = request("CSTRAB", "0101T01");

                assertThrows(ResponseStatusException.class, () -> service.upload(request, " ", "USER"));
                assertThrows(ResponseStatusException.class,
                                () -> service.upload(request, "user@example.com", "OPERATOR"));

                verifyNoInteractions(jdbcTemplate, transactionService, databaseInstanceService);
        }

        @Test
        void rejectsDatabaseNamesOutsideTheAllowedPattern() {
                UploadRequest request = request("master", "0101T01");

                assertThrows(ResponseStatusException.class, () -> service.upload(request, "user@example.com", "USER"));

                verifyNoInteractions(jdbcTemplate, transactionService, databaseInstanceService);
        }

        @Test
        void deduplicatesRepeatedIdenticalGroupSelectionsInOneRequest() {
                when(jdbcTemplate.queryForObject(contains("OUTPUT INSERTED.UploadGroupResultId"), eq(Long.class),
                                any(), any(), any()))
                                .thenReturn(91L);
                doReturn(List.of()).when(jdbcTemplate).query(
                                anyString(), ArgumentMatchers.<RowMapper<BillingUploadService.RouteRecord>>any(),
                                ArgumentMatchers.<Object[]>any());

                UploadSelection selection = new UploadSelection("CSTRAB", "0101T01");
                var response = service.upload(new UploadRequest(List.of(selection, selection)), "user@example.com",
                                "USER");

                assertEquals(1, response.failedCount());
                assertEquals(1, response.results().size());
                verify(jdbcTemplate, times(1)).queryForObject(
                                contains("INSERT INTO [TRUpload].[dbo].[UploadGroupResult]"),
                                eq(Long.class), any(), any(), any());
        }

        @Test
        void rejectsPreviouslyUploadedGroupBeforeLookingUpSourceRoutes() {
                when(jdbcTemplate.queryForObject(
                                contains("SELECT CASE WHEN EXISTS"), eq(Integer.class),
                                eq("CSTRAB"), eq("0216Y16"), eq("0216Y16wave")))
                                .thenReturn(1);
                when(jdbcTemplate.queryForObject(contains("OUTPUT INSERTED.UploadGroupResultId"),
                                eq(Long.class), any(), any(), any()))
                                .thenReturn(401L);

                var response = service.upload(request("CSTRAB", "0216Y16"), "user@example.com", "USER");

                assertEquals(0, response.uploadedCount());
                assertEquals(1, response.failedCount());
                assertEquals(1, response.results().size());
                assertEquals("0216Y16", response.results().get(0).routeGroupId());
                assertEquals("FAILED", response.results().get(0).status());
                assertEquals("This billing group has already been uploaded.",
                                response.results().get(0).reasons().get(0));

                verify(jdbcTemplate).queryForObject(
                                contains("SELECT CASE WHEN EXISTS"), eq(Integer.class),
                                eq("CSTRAB"), eq("0216Y16"), eq("0216Y16wave"));
                verify(jdbcTemplate, org.mockito.Mockito.never()).query(
                                anyString(),
                                ArgumentMatchers.<RowMapper<BillingUploadService.RouteRecord>>any(),
                                ArgumentMatchers.<Object[]>any());
                verifyNoInteractions(transactionService);
        }

        @Test
        void combinesValidationAndDuplicateReasonsInResponseAndFailurePersistence() {
                LocalDate dispatchDate = LocalDate.of(2024, 1, 1);
                doReturn(List.of(new BillingUploadService.RouteRecord("0ROUTE", dispatchDate)))
                                .when(jdbcTemplate).query(
                                                anyString(),
                                                ArgumentMatchers.<RowMapper<BillingUploadService.RouteRecord>>any(),
                                                ArgumentMatchers.<Object[]>any());
                when(jdbcTemplate.queryForObject(contains("OUTPUT INSERTED.UploadGroupResultId"), eq(Long.class),
                                any(), any(), any()))
                                .thenReturn(92L);
                when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class),
                                any(), any(), any(), any(), any()))
                                .thenReturn(1);

                var response = service.upload(request("CSTRAB", "0101T01"), "user@example.com", "USER");

                assertEquals(1, response.failedCount());
                assertEquals(2, response.results().get(0).reasons().size());
                assertTrue(response.results().get(0).reasons().get(0).contains("weekday digit"));
                assertTrue(response.results().get(0).reasons().get(1).contains("already been uploaded"));
                verify(jdbcTemplate).queryForObject(
                                contains("LOWER(RTRIM(RouteGroupId)) = LOWER(CONCAT(?, 'wave'))"),
                                eq(Integer.class), eq("CSTRAB"), eq("0ROUTE"), eq("0101T01"),
                                eq("0101T01"), eq(dispatchDate));

                ArgumentCaptor<Object[]> persistedParameters = ArgumentCaptor.forClass(Object[].class);
                verify(jdbcTemplate).update(contains("INSERT INTO [TRUpload].[dbo].[UploadFailedBilling]"),
                                persistedParameters.capture());
                assertEquals(String.join("; ", response.results().get(0).reasons()), persistedParameters.getValue()[5]);
                verifyNoInteractions(transactionService);
        }

        @Test
        void rejectsEntireGroupWhenOneRouteFailsValidation() {
                LocalDate dispatchDate = LocalDate.of(2024, 1, 1);

                // Route 1 is invalid: 0 is not a valid weekday digit.
                // Route 2 is valid: 1 represents Monday.
                doReturn(List.of(
                                new BillingUploadService.RouteRecord("012345", dispatchDate),
                                new BillingUploadService.RouteRecord("112346", dispatchDate)))
                                .when(jdbcTemplate).query(
                                                anyString(),
                                                ArgumentMatchers.<RowMapper<BillingUploadService.RouteRecord>>any(),
                                                ArgumentMatchers.<Object[]>any());

                when(jdbcTemplate.queryForObject(
                                contains("OUTPUT INSERTED.UploadGroupResultId"),
                                eq(Long.class), any(), any(), any()))
                                .thenReturn(101L);

                // Neither route is already uploaded.
                when(jdbcTemplate.queryForObject(
                                contains("SELECT COUNT(*)"),
                                eq(Integer.class), any(), any(), any(), any(), any()))
                                .thenReturn(0);

                var response = service.upload(
                                request("CSTRAB", "0101T01"),
                                "admin@example.com",
                                "ADMIN");

                // Both routes must fail because the group is rejected as a whole.
                assertEquals(0, response.uploadedCount());
                assertEquals(2, response.failedCount());
                assertEquals(2, response.results().size());

                assertEquals("FAILED", response.results().get(0).status());
                assertTrue(response.results().get(0).reasons().stream()
                                .anyMatch(reason -> reason.contains("weekday digit")));

                assertEquals("FAILED", response.results().get(1).status());
                assertTrue(response.results().get(1).reasons().stream()
                                .anyMatch(reason -> reason.contains("Group rejected")));

                // No successful billing transaction should be attempted.
                verifyNoInteractions(transactionService);

                // Both routes should have failure records persisted.
                verify(jdbcTemplate, times(2)).update(
                                contains("INSERT INTO [TRUpload].[dbo].[UploadFailedBilling]"),
                                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

                // The group and batch should both be marked FAILED.
                verify(jdbcTemplate).update(
                                contains("SET Status = 'FAILED'"),
                                eq(2), eq(2), any(), eq(101L));

                verify(jdbcTemplate).update(
                                contains("UPDATE [TRUpload].[dbo].[UploadBatch]"),
                                eq("FAILED"), eq(0), eq(2), eq(0), any());
        }

        @Test
        void recordsGroupFailuresWhenTransactionalInsertThrowsDuplicateKeyException() {
                LocalDate dispatchDate = LocalDate.of(2024, 1, 1);

                doReturn(List.of(
                                new BillingUploadService.RouteRecord("112345", dispatchDate),
                                new BillingUploadService.RouteRecord("112346", dispatchDate)))
                                .when(jdbcTemplate).query(
                                                anyString(),
                                                ArgumentMatchers.<RowMapper<BillingUploadService.RouteRecord>>any(),
                                                ArgumentMatchers.<Object[]>any());

                when(jdbcTemplate.queryForObject(
                                contains("OUTPUT INSERTED.UploadGroupResultId"),
                                eq(Long.class), any(), any(), any()))
                                .thenReturn(102L);

                // The initial duplicate checks find no existing records.
                // The checks performed by the exception handler also find none,
                // so the failure reason identifies a group-level insert conflict.
                when(jdbcTemplate.queryForObject(
                                contains("SELECT COUNT(*)"),
                                eq(Integer.class), any(), any(), any(), any(), any()))
                                .thenReturn(0);

                when(transactionService.insertSuccessfulGroup(
                                eq(102L),
                                eq("CSTRAB"),
                                eq("0101T01"),
                                eq("admin@example.com"),
                                eq(false),
                                ArgumentMatchers.<List<BillingGroupTransactionService.RouteToUpload>>any()))
                                .thenThrow(new DuplicateKeyException("Simulated duplicate-key conflict"));

                var response = service.upload(
                                request("CSTRAB", "0101T01"),
                                "admin@example.com",
                                "ADMIN");

                assertEquals(0, response.uploadedCount());
                assertEquals(2, response.failedCount());
                assertEquals(2, response.results().size());

                assertTrue(response.results().stream()
                                .allMatch(result -> "FAILED".equals(result.status())));

                assertTrue(response.results().stream()
                                .allMatch(result -> result.reasons().stream()
                                                .anyMatch(reason -> reason.contains("duplicate conflict"))));

                verify(transactionService, times(1)).insertSuccessfulGroup(
                                eq(102L),
                                eq("CSTRAB"),
                                eq("0101T01"),
                                eq("admin@example.com"),
                                eq(false),
                                ArgumentMatchers.<List<BillingGroupTransactionService.RouteToUpload>>any());

                verify(jdbcTemplate, times(2)).update(
                                contains("INSERT INTO [TRUpload].[dbo].[UploadFailedBilling]"),
                                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

                verify(jdbcTemplate).update(
                                contains("SET Status = 'FAILED'"),
                                eq(2), eq(2), any(), eq(102L));

                verify(jdbcTemplate).update(
                                contains("UPDATE [TRUpload].[dbo].[UploadBatch]"),
                                eq("FAILED"), eq(0), eq(2), eq(0), any());
        }

        @Test
        void reportsMixedGroupOutcomesAndPartiallyFailedBatchCounts() {
                when(jdbcTemplate.queryForObject(contains("OUTPUT INSERTED.UploadGroupResultId"), eq(Long.class),
                                any(), any(), any()))
                                .thenReturn(201L, 202L);
                when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class),
                                any(), any(), any(), any(), any()))
                                .thenReturn(0);
                doAnswer(invocation -> {
                        String groupId = invocation.getArgument(2, String.class);
                        LocalDate routeDate = groupId.equals("0101T01")
                                        ? LocalDate.of(2024, 1, 1)
                                        : LocalDate.of(2024, 1, 2);
                        String routeId = groupId.equals("0101T01") ? "212345" : "212346";
                        return List.of(new BillingUploadService.RouteRecord(routeId, routeDate));
                }).when(jdbcTemplate).query(anyString(),
                                ArgumentMatchers.<RowMapper<BillingUploadService.RouteRecord>>any(),
                                ArgumentMatchers.<Object[]>any());
                when(transactionService.insertSuccessfulGroup(
                                eq(202L), eq("CSTRAB"), eq("0102T01"), eq("admin@example.com"),
                                eq(false), anyList()))
                                .thenReturn(new BillingGroupTransactionService.GroupInsertResult("SUCCESS", 1, 0));

                var response = service.upload(
                                new UploadRequest(List.of(
                                                new UploadSelection("CSTRAB", "0101T01"),
                                                new UploadSelection("CSTRAB", "0102T01"))),
                                "admin@example.com", "ADMIN");

                assertEquals(1, response.uploadedCount());
                assertEquals(1, response.failedCount());
                assertEquals(2, response.results().size());
                assertEquals("FAILED", response.results().get(0).status());
                assertEquals("UPLOADED", response.results().get(1).status());
                assertEquals("0102T01wave", response.results().get(1).routeGroupId());

                verify(jdbcTemplate).update(contains("UPDATE [TRUpload].[dbo].[UploadBatch]"),
                                eq("PARTIALLY_FAILED"), eq(1), eq(1), any(), any());
                verify(transactionService).insertSuccessfulGroup(
                                eq(202L), eq("CSTRAB"), eq("0102T01"), eq("admin@example.com"),
                                eq(false), anyList());
        }

        @Test
        void duplicateIdentityUsesDatabaseRouteGroupAndCalendarDateOnly() {
                LocalDate date = LocalDate.of(2024, 10, 14);
                LocalDateTime dispatchAtMorning = date.atTime(1, 0);
                LocalDateTime dispatchAtNight = date.atTime(23, 59);
                BillingUploadService.BillingDuplicateKey original = new BillingUploadService.BillingDuplicateKey(
                                "CSTRAB", "412345", "1014T22", dispatchAtMorning.toLocalDate());
                BillingUploadService.BillingDuplicateKey sameBusinessDate = new BillingUploadService.BillingDuplicateKey(
                                "CSTRAB", "412345", "1014T22", dispatchAtNight.toLocalDate());

                assertEquals(original, sameBusinessDate);
                assertNotEquals(original, new BillingUploadService.BillingDuplicateKey(
                                "CSTRAB", "512345", "1014T22", date));
                assertNotEquals(original, new BillingUploadService.BillingDuplicateKey(
                                "CSTRAB", "412345", "1015T22", date));
                assertNotEquals(original, new BillingUploadService.BillingDuplicateKey(
                                "CSTRBE", "412345", "1014T22", date));
                assertNotEquals(original, new BillingUploadService.BillingDuplicateKey(
                                "CSTRAB", "412345", "1014T22", date.plusDays(1)));
        }

        @Test
        void unexpectedDatabaseFailureMarksCurrentGroupAndBatchFailedThenRethrows() {
                DataAccessResourceFailureException databaseFailure = new DataAccessResourceFailureException(
                                "simulated SQL Server connection failure");
                when(jdbcTemplate.queryForObject(contains("OUTPUT INSERTED.UploadGroupResultId"), eq(Long.class),
                                any(), any(), any()))
                                .thenReturn(301L);
                doAnswer(invocation -> {
                        throw databaseFailure;
                }).when(jdbcTemplate).query(anyString(),
                                ArgumentMatchers.<RowMapper<BillingUploadService.RouteRecord>>any(),
                                ArgumentMatchers.<Object[]>any());

                DataAccessResourceFailureException thrown = assertThrows(
                                DataAccessResourceFailureException.class,
                                () -> service.upload(request("CSTRAB", "0101T01"), "user@example.com", "USER"));

                assertEquals(databaseFailure, thrown);
                verify(jdbcTemplate).update(contains("UPDATE [TRUpload].[dbo].[UploadGroupResult]"),
                                contains("Unexpected upload processing error"), eq(301L));
                verify(jdbcTemplate).update(contains("UPDATE [TRUpload].[dbo].[UploadBatch]"),
                                eq(0), eq(0), eq(0), any());
                verifyNoInteractions(transactionService);
        }

        @Test
        void allowsAdminAndUserRoleValuesToReachGroupProcessing() {
                when(jdbcTemplate.queryForObject(contains("OUTPUT INSERTED.UploadGroupResultId"), eq(Long.class),
                                any(), any(), any()))
                                .thenReturn(93L);
                doReturn(List.of()).when(jdbcTemplate).query(
                                anyString(), ArgumentMatchers.<RowMapper<BillingUploadService.RouteRecord>>any(),
                                ArgumentMatchers.<Object[]>any());

                assertTrue(service.upload(request("CSTRAB", "0101T01"), "admin@example.com", "ADMIN")
                                .results().get(0).reasons().get(0).contains("No eligible route"));
                assertTrue(service.upload(request("CSTRAB", "0101T01"), "user@example.com", "USER")
                                .results().get(0).reasons().get(0).contains("No eligible route"));
        }

        private UploadRequest request(String databaseName, String routeGroupId) {
                return new UploadRequest(List.of(new UploadSelection(databaseName, routeGroupId)));
        }
}
