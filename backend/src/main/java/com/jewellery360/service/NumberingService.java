package com.jewellery360.service;

import com.jewellery360.domain.Company;
import com.jewellery360.domain.DocumentSequence;
import com.jewellery360.repository.DocumentSequenceRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.function.Predicate;

/**
 * Gapless, per-company document numbers per Indian financial year: INV/26-27/0001, RCPT/26-27/0001 ...
 * The sequence row is locked for the rest of the caller's transaction, so two counters can never hand out the same number,
 * and a rolled-back bill gives its number back.
 */
@Service
@RequiredArgsConstructor
public class NumberingService {
    private final DocumentSequenceRepository sequences;
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Company company, String prefix, LocalDate date) {
        return next(company, prefix, date, no -> false);
    }

    /** Same as {@link #next(Company, String, LocalDate)}, skipping numbers already used (e.g. typed in by hand). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Company company, String prefix, LocalDate date, Predicate<String> taken) {
        String fy = financialYear(date);
        String key = prefix + "-" + fy;
        em.createNativeQuery("INSERT INTO document_sequence (company_id, doc_key, next_value) VALUES (?1, ?2, 1) ON CONFLICT (company_id, doc_key) DO NOTHING")
                .setParameter(1, company.getId()).setParameter(2, key).executeUpdate();
        DocumentSequence seq = sequences.lock(company.getId(), key).orElseThrow();
        long n = seq.getNextValue();
        while (taken.test(format(prefix, fy, n))) n++;
        seq.setNextValue(n + 1);
        return format(prefix, fy, n);
    }

    /** The number {@link #next} would hand out now, without reserving it (another counter may still take it first). */
    @Transactional(readOnly = true)
    public String peek(Company company, String prefix, LocalDate date, Predicate<String> taken) {
        String fy = financialYear(date);
        long n = sequences.findByCompanyIdAndDocKey(company.getId(), prefix + "-" + fy).map(DocumentSequence::getNextValue).orElse(1L);
        while (taken.test(format(prefix, fy, n))) n++;
        return format(prefix, fy, n);
    }

    private static String format(String prefix, String fy, long n) {
        return prefix + "/" + fy + "/" + (n < 10000 ? String.format("%04d", n) : Long.toString(n));
    }

    /** 2026-10-12 -> "26-27" (April to March). */
    static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return String.format("%02d-%02d", start % 100, (start + 1) % 100);
    }
}
