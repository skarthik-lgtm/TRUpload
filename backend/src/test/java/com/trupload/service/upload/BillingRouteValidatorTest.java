package com.trupload.service.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

class BillingRouteValidatorTest {

    private BillingRouteValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BillingRouteValidator();
    }

    @ParameterizedTest
    @CsvSource({
            "1, 2024-01-01, 0101T01",
            "2, 2024-01-02, 0102T01",
            "3, 2024-01-03, 0103T01",
            "4, 2024-01-04, 0104T01",
            "5, 2024-01-05, 0105T01",
            "6, 2024-01-06, 0106T01",
            "7, 2024-01-07, 0107T01"
    })
    void acceptsIsoWeekdayDigits(String weekdayDigit, LocalDate dispatchDate, String groupId) {
        assertTrue(validator.validate(weekdayDigit + "12345", dispatchDate, groupId).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "0ROUTE",
            "8ROUTE",
            "XROUTE",
            "' '",
            "''"
    })
    void rejectsInvalidRouteIdFirstDigit(String routeId) {
        List<String> reasons = validator.validate(routeId, LocalDate.of(2024, 1, 1), "0101T01");
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("weekday digit")));
    }

    @Test
    void nullRouteIdAndDispatchDateReturnValidationReasons() {
        List<String> reasons = validator.validate(null, null, "0101T01");
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("weekday digit")));
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("DispatchDateTime is missing")));
    }

    @ParameterizedTest
    @MethodSource("invalidGroupIds")
    void rejectsInvalidOrMalformedGroupDate(String groupId) {
        List<String> reasons = validator.validate("112345", LocalDate.of(2024, 1, 1), groupId);
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("valid MMDD")));
    }

    static Stream<Arguments> invalidGroupIds() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("010"),
                Arguments.of("0A01T01"),
                Arguments.of("0230T01"));
    }

    @Test
    void acceptsLeapDayWhenDispatchIsInLeapYear() {
        assertTrue(validator.validate("422345", LocalDate.of(2024, 2, 29), "0229T01").isEmpty());
    }

    @Test
    void handlesLeapDayGroupDateOutsideLeapYearAsAnOutOfWindowDate() {
        List<String> reasons = validator.validate("612345", LocalDate.of(2025, 3, 1), "0229T01");
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("more than 3 days")));
    }

    @Test
    void rejectsDispatchDateBeforeGroupDate() {
        List<String> reasons = validator.validate("712345", LocalDate.of(2023, 12, 31), "0101T01");
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("before the MMDD date")));
    }

    @ParameterizedTest
    @CsvSource({
            "2024-01-01, false",
            "2024-01-02, false",
            "2024-01-03, false",
            "2024-01-04, false",
            "2024-01-05, true",
            "2024-01-06, true"
    })
    void enforcesGroupDateWindow(LocalDate dispatchDate, boolean expectedToFail) {
        int weekday = dispatchDate.getDayOfWeek().getValue();
        List<String> reasons = validator.validate(weekday + "12345", dispatchDate, "0101T01");
        assertEquals(expectedToFail,
                reasons.stream().anyMatch(reason -> reason.contains("before the MMDD date")
                        || reason.contains("more than 3 days")));
    }

    @Test
    void resolvesYearBoundaryToNearestGroupDate() {
        LocalDate dispatchDate = LocalDate.of(2025, 1, 2);
        assertEquals(LocalDate.of(2024, 12, 30), validator.nearestGroupDate("1230T01", dispatchDate));
        assertTrue(validator.validate("4ROUTE", dispatchDate, "1230T01").isEmpty());
    }

    @Test
    void dispatchTimeDoesNotChangeWeekdayOrDateValidation() {
        LocalDateTime early = LocalDateTime.of(2024, 1, 2, 0, 1);
        LocalDateTime late = LocalDateTime.of(2024, 1, 2, 23, 59);

        List<String> earlyReasons = validator.validate("212345", early.toLocalDate(), "0102T01");
        List<String> lateReasons = validator.validate("212345", late.toLocalDate(), "0102T01");

        assertEquals(earlyReasons, lateReasons);
        assertTrue(earlyReasons.isEmpty());
    }

    @Test
    void returnsAllIndependentValidationReasonsWithoutRepeatingMessages() {
        List<String> reasons = validator.validate("0ROUTE", LocalDate.of(2024, 1, 5), "0101T01");

        assertEquals(2, reasons.size());
        assertEquals(reasons.size(), reasons.stream().distinct().count());
        assertTrue(reasons.get(0).contains("weekday digit"));
        assertTrue(reasons.get(1).contains("more than 3 days"));
    }
}
