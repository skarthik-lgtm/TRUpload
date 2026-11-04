package com.trupload.service.upload;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

final class BillingRouteValidator {

    List<String> validate(String routeIdValue, LocalDate dispatchDate, String routeGroupId) {
        List<String> reasons = new ArrayList<>();
        String routeId = routeIdValue == null ? "" : routeIdValue.trim();

        boolean hasValidWeekdayDigit = !routeId.isEmpty()
                && routeId.charAt(0) >= '1'
                && routeId.charAt(0) <= '7';

        if (!hasValidWeekdayDigit) {
            reasons.add("RouteId must begin with a weekday digit from 1 (Monday) through 7 (Sunday).");
        }

        if (dispatchDate == null) {
            reasons.add("DispatchDateTime is missing, so its date and weekday cannot be validated.");
        } else if (hasValidWeekdayDigit) {
            int routeWeekday = routeId.charAt(0) - '0';
            int dispatchWeekday = dispatchDate.getDayOfWeek().getValue();
            if (routeWeekday != dispatchWeekday) {
                reasons.add("RouteId weekday does not match DispatchDateTime ("
                        + weekdayName(routeWeekday) + " expected, "
                        + weekdayName(dispatchWeekday) + " found).");
            }
        }

        if (!hasValidMmdd(routeGroupId)) {
            reasons.add("RouteGroupId must begin with a valid MMDD date.");
        } else if (dispatchDate != null) {
            LocalDate groupDate = nearestGroupDate(routeGroupId, dispatchDate);
            if (groupDate == null) {
                reasons.add("RouteGroupId must begin with a valid MMDD date.");
            } else {
                long dayDifference = ChronoUnit.DAYS.between(groupDate, dispatchDate);
                if (dayDifference < 0) {
                    reasons.add("Dispatch date is before the MMDD date in RouteGroupId.");
                } else if (dayDifference > 3) {
                    reasons.add("Dispatch date is more than 3 days after the MMDD date in RouteGroupId.");
                }
            }
        }

        return List.copyOf(reasons);
    }

    LocalDate nearestGroupDate(String routeGroupId, LocalDate dispatchDate) {
        if (!hasValidMmdd(routeGroupId) || dispatchDate == null) {
            return null;
        }

        int month = Integer.parseInt(routeGroupId.substring(0, 2));
        int day = Integer.parseInt(routeGroupId.substring(2, 4));
        return IntStream.rangeClosed(dispatchDate.getYear() - 1, dispatchDate.getYear() + 1)
                .mapToObj(year -> {
                    try {
                        return LocalDate.of(year, month, day);
                    } catch (DateTimeException invalidDate) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingLong(candidate -> Math.abs(
                        ChronoUnit.DAYS.between(candidate, dispatchDate))))
                .orElse(null);
    }

    private boolean hasValidMmdd(String routeGroupId) {
        if (routeGroupId == null || routeGroupId.length() < 4
                || !routeGroupId.substring(0, 4).matches("\\d{4}")) {
            return false;
        }

        int month = Integer.parseInt(routeGroupId.substring(0, 2));
        int day = Integer.parseInt(routeGroupId.substring(2, 4));
        return IntStream.rangeClosed(2000, 2003)
                .mapToObj(year -> {
                    try {
                        return LocalDate.of(year, month, day);
                    } catch (DateTimeException invalidDate) {
                        return null;
                    }
                })
                .anyMatch(Objects::nonNull);
    }

    private String weekdayName(int day) {
        return switch (day) {
            case 1 -> "Monday";
            case 2 -> "Tuesday";
            case 3 -> "Wednesday";
            case 4 -> "Thursday";
            case 5 -> "Friday";
            case 6 -> "Saturday";
            case 7 -> "Sunday";
            default -> "Unknown";
        };
    }
}
