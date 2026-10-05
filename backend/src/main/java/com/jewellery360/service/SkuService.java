package com.jewellery360.service;

import com.jewellery360.domain.Company;
import com.jewellery360.domain.JewelleryProduct;
import com.jewellery360.domain.JewelleryTag;
import com.jewellery360.repository.JewelleryProductRepository;
import com.jewellery360.repository.JewelleryTagRepository;
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
 * Tenant-isolated sequence generator for product SKUs and tag numbers.
 *
 * Every lookup is scoped by company id, so two companies can both own "01", "02", ... without ever clashing, and
 * one company's sequence is never influenced by another's. The company can set an alphanumeric prefix in Settings
 * (property SKU_PREFIX); with no prefix the numbers start at 01.
 *
 * The next value is derived from the highest number already used for that company and prefix (plus a uniqueness guard),
 * so deleting rows never causes a number to be handed out twice. The database unique constraints remain the final
 * safeguard against two simultaneous requests picking the same value.
 */
@Service
@RequiredArgsConstructor
public class SkuService {
    private final JewelleryProductRepository products;
    private final JewelleryTagRepository tags;
    private final CompanyPropertyService companyProperties;

    @Transactional
    public String nextProductSku(Company company) {
        Set<String> used = new HashSet<>();
        for (JewelleryProduct p : products.findByCompanyId(company.getId())) {
            if (p.getSku() != null) used.add(p.getSku().trim().toUpperCase(Locale.ROOT));
        }
        return next(prefix(company), used);
    }

    @Transactional
    public String nextTagNo(Company company) {
        Set<String> used = new HashSet<>();
        for (JewelleryTag t : tags.findByCompanyId(company.getId())) {
            if (t.getTagNo() != null) used.add(t.getTagNo().trim().toUpperCase(Locale.ROOT));
        }
        return next(prefix(company), used);
    }

    private String prefix(Company company) {
        String value = companyProperties.getBusinessProfile(company).skuPrefix();
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String next(String prefix, Collection<String> used) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$");
        long max = 0;
        for (String value : used) {
            Matcher m = pattern.matcher(value);
            if (m.matches()) {
                try {
                    max = Math.max(max, Long.parseLong(m.group(1)));
                } catch (NumberFormatException ignored) {
                    // an oversized numeric suffix is simply not part of the sequence
                }
            }
        }
        long candidateNo = max + 1;
        String candidate = format(prefix, candidateNo);
        while (used.contains(candidate)) {
            candidateNo++;
            candidate = format(prefix, candidateNo);
        }
        return candidate;
    }

    private static String format(String prefix, long number) {
        return prefix + (number < 10 ? "0" + number : Long.toString(number));
    }
}
