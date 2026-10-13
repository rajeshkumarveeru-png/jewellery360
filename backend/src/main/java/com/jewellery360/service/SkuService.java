package com.jewellery360.service;

import com.jewellery360.domain.Company;
import com.jewellery360.repository.JewelleryItemRepository;
import com.jewellery360.repository.JewelleryProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-company product SKUs and tag numbers: 01, 02 ... or PREFIX01 when the company sets an SKU prefix in Settings.
 * The next value follows the highest number already used, so deleting never re-issues a number; the unique constraints
 * stay the final guard.
 */
@Service
@RequiredArgsConstructor
public class SkuService {
    private final JewelleryProductRepository products;
    private final JewelleryItemRepository items;
    private final CompanyPropertyService companyProperties;

    @Transactional(readOnly = true)
    public String nextProductSku(Company company) {
        return next("P" + prefix(company), products.skus(company.getId()));
    }

    @Transactional(readOnly = true)
    public String nextTagNo(Company company) {
        return next(prefix(company), items.tagNumbers(company.getId()));
    }

    private String prefix(Company company) {
        String value = companyProperties.getBusinessProfile(company).skuPrefix();
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    static String next(String prefix, Collection<String> usedRaw) {
        Set<String> used = new HashSet<>();
        for (String u : usedRaw) if (u != null) used.add(u.trim().toUpperCase(Locale.ROOT));
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$");
        long max = 0;
        for (String value : used) {
            Matcher m = pattern.matcher(value);
            if (m.matches() && m.group(1).length() < 18) max = Math.max(max, Long.parseLong(m.group(1)));
        }
        long n = max + 1;
        String candidate = format(prefix, n);
        while (used.contains(candidate)) candidate = format(prefix, ++n);
        return candidate;
    }

    private static String format(String prefix, long number) {
        return prefix + (number < 10 ? "0" + number : Long.toString(number));
    }
}
