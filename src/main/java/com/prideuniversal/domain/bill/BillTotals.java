package com.prideuniversal.domain.bill;

import java.math.BigDecimal;

/** How many bills, and how much they add up to (amount plus late fee). */
public record BillTotals(long count, BigDecimal amount) {}
