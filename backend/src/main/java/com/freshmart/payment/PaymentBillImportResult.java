package com.freshmart.payment;

public record PaymentBillImportResult(long importId, int totalEntries, int matchedEntries, int differenceEntries) {
}
