package com.trupload.api.database;

public record OrderResponse(
        String orderId,
        Object q1,
        Object q2,
        Object q3,
        Object q4
) {
}