package com.jewellery360.service;

import com.jewellery360.domain.Company;
import com.jewellery360.domain.JewelleryCategory;
import com.jewellery360.domain.PurityMaster;
import com.jewellery360.repository.CompanyRepository;
import com.jewellery360.repository.JewelleryCategoryRepository;
import com.jewellery360.repository.PurityMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Gives every company the standard purities and a few common categories so the first piece can be added straight away.
 * Nothing else is seeded: no demo stock, no demo prices - gold rates must come from the shop.
 */
@Service
@RequiredArgsConstructor
public class CompanySetupService {
    private final CompanyRepository companies;
    private final PurityMasterRepository purities;
    private final JewelleryCategoryRepository categories;

    private static final Object[][] PURITIES = {
            {"GOLD", "24K", "24K", "0.9990", "999 fine gold"},
            {"GOLD", "22K", "22K", "0.9160", "916 hallmark gold"},
            {"GOLD", "18K", "18K", "0.7500", "750 gold"},
            {"GOLD", "14K", "14K", "0.5850", "585 gold"},
            {"SILVER", "Silver 999", null, "0.9990", "Fine silver"},
            {"SILVER", "Silver 925", null, "0.9250", "Sterling silver"},
    };
    private static final String[][] CATEGORIES = {{"Rings", "RING"}, {"Chains", "CHAIN"}, {"Bangles", "BANGLE"}, {"Necklaces", "NECKLACE"},
            {"Earrings", "EARRING"}, {"Pendants", "PENDANT"}, {"Coins", "COIN"}, {"Silver articles", "SILVER"}};

    @Transactional
    public void setUp(Company company) {
        if (company == null || company.getId() == null || purities.existsByCompanyId(company.getId())) return;
        for (Object[] p : PURITIES) {
            PurityMaster m = new PurityMaster();
            m.setCompany(company);
            m.setMetal((String) p[0]);
            m.setName((String) p[1]);
            m.setKarat((String) p[2]);
            m.setFineness(new BigDecimal((String) p[3]));
            m.setDescription((String) p[4]);
            purities.save(m);
        }
        for (String[] c : CATEGORIES) {
            if (categories.findByCompanyIdAndCodeIgnoreCase(company.getId(), c[1]).isPresent()) continue;
            JewelleryCategory jc = new JewelleryCategory();
            jc.setCompany(company);
            jc.setName(c[0]);
            jc.setCode(c[1]);
            categories.save(jc);
        }
    }

    /** Companies created before this existed (or by an older version) get the same starter set once. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void setUpExisting() {
        for (Company c : companies.findAll()) if (c.isActive()) setUp(c);
    }
}
