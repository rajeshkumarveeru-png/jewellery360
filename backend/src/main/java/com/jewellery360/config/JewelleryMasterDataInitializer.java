package com.jewellery360.config;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.*;

/**
 * Creates a small, deterministic starter jewellery master set for an empty company.
 *
 * The seed is intentionally idempotent: existing master records are reused and never
 * overwritten. No gold-rate values are seeded because those are business-sensitive
 * and must come from the retailer's actual rate source.
 */
@Configuration
@RequiredArgsConstructor
public class JewelleryMasterDataInitializer {

    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final PurityMasterRepository purities;
    private final JewelleryCategoryRepository categories;
    private final JewelleryDesignRepository designs;
    private final JewelleryProductRepository products;
    private final JewelleryTagRepository tags;
    private final JewelleryItemRepository items;

    @Bean
    CommandLineRunner seedJewelleryMasterData() {
        return args -> {
            for (Company company : companies.findAll()) {
                if (!company.isActive()) continue;

                List<Branch> companyBranches = branches.findByCompanyId(company.getId()).stream()
                        .filter(Branch::isActive)
                        .toList();
                if (companyBranches.isEmpty()) continue;

                Branch branch = companyBranches.get(0);

                PurityMaster purity22 = purity(company, "22K", "22K", new BigDecimal("0.916"), "916 Gold");
                PurityMaster purity24 = purity(company, "24K", "24K", new BigDecimal("0.999"), "999 Fine Gold");
                purity(company, "20K", "20K", new BigDecimal("0.835"), "835 Gold");
                purity(company, "18K", "18K", new BigDecimal("0.750"), "750 Gold");
                purity(company, "14K", "14K", new BigDecimal("0.585"), "585 Gold");

                JewelleryCategory rings = category(company, "Gold Rings", "RING");
                JewelleryCategory chains = category(company, "Gold Chains", "CHAIN");
                JewelleryCategory bangles = category(company, "Gold Bangles", "BANGLE");
                JewelleryCategory necklaces = category(company, "Gold Necklaces", "NECK");

                JewelleryDesign classicRing = design(company, rings, "Classic Gold Ring", "RING-CLASSIC");
                JewelleryDesign royalChain = design(company, chains, "Royal Gold Chain", "CHAIN-ROYAL");
                JewelleryDesign templeBangle = design(company, bangles, "Temple Gold Bangle", "BANGLE-TEMPLE");
                JewelleryDesign bridalNecklace = design(company, necklaces, "Bridal Gold Necklace", "NECK-BRIDAL");

                JewelleryProduct ring = product(company, rings, classicRing,
                        "22K Classic Gold Ring", "RING-22K-001");
                JewelleryProduct chain = product(company, chains, royalChain,
                        "22K Royal Gold Chain", "CHAIN-22K-001");
                JewelleryProduct bangle = product(company, bangles, templeBangle,
                        "22K Temple Gold Bangle", "BANGLE-22K-001");
                JewelleryProduct necklace = product(company, necklaces, bridalNecklace,
                        "22K Bridal Gold Necklace", "NECK-22K-001");

                tagAndItem(company, branch, ring, purity22, "TAG-R-0001", "890000000001",
                        new BigDecimal("8.250"), new BigDecimal("0.150"), new BigDecimal("7.100"));
                tagAndItem(company, branch, chain, purity22, "TAG-C-0001", "890000000002",
                        new BigDecimal("18.500"), new BigDecimal("0.300"), new BigDecimal("17.900"));
                tagAndItem(company, branch, bangle, purity22, "TAG-B-0001", "890000000003",
                        new BigDecimal("12.750"), new BigDecimal("0.250"), new BigDecimal("12.100"));
                tagAndItem(company, branch, necklace, purity24, "TAG-N-0001", "890000000004",
                        new BigDecimal("25.200"), new BigDecimal("0.450"), new BigDecimal("24.500"));
            }
        };
    }

    private PurityMaster purity(Company company, String name, String karat,
                                 BigDecimal fineness, String description) {
        return purities.findByCompanyId(company.getId()).stream()
                .filter(x -> name.equalsIgnoreCase(x.getName()))
                .findFirst()
                .map(x -> {
                    if (x.getDescription() == null || x.getDescription().isBlank()) {
                        x.setDescription(description);
                        return purities.save(x);
                    }
                    return x;
                })
                .orElseGet(() -> {
                    PurityMaster x = new PurityMaster();
                    x.setCompany(company);
                    x.setName(name);
                    x.setKarat(karat);
                    x.setFineness(fineness);
                    x.setDescription(description);
                    x.setActive(true);
                    return purities.save(x);
                });
    }

    private JewelleryCategory category(Company company, String name, String code) {
        return categories.findByCompanyId(company.getId()).stream()
                .filter(x -> code.equalsIgnoreCase(x.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    JewelleryCategory x = new JewelleryCategory();
                    x.setCompany(company);
                    x.setName(name);
                    x.setCode(code);
                    x.setActive(true);
                    return categories.save(x);
                });
    }

    private JewelleryDesign design(Company company, JewelleryCategory category, String name, String code) {
        return designs.findByCompanyId(company.getId()).stream()
                .filter(x -> code.equalsIgnoreCase(x.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    JewelleryDesign x = new JewelleryDesign();
                    x.setCompany(company);
                    x.setCategory(category);
                    x.setName(name);
                    x.setCode(code);
                    x.setDescription("Starter master design");
                    x.setActive(true);
                    return designs.save(x);
                });
    }

    private JewelleryProduct product(Company company, JewelleryCategory category,
                                     JewelleryDesign design, String name, String sku) {
        return products.findByCompanyId(company.getId()).stream()
                .filter(x -> sku.equalsIgnoreCase(x.getSku()))
                .findFirst()
                .orElseGet(() -> {
                    JewelleryProduct x = new JewelleryProduct();
                    x.setCompany(company);
                    x.setCategory(category);
                    x.setDesign(design);
                    x.setName(name);
                    x.setSku(sku);
                    x.setDescription("Starter jewellery product");
                    x.setActive(true);
                    return products.save(x);
                });
    }

    private void tagAndItem(Company company, Branch branch, JewelleryProduct product,
                            PurityMaster purity, String tagNo, String barcode,
                            BigDecimal gross, BigDecimal stone, BigDecimal net) {
        JewelleryTag tag = tags.findByCompanyId(company.getId()).stream()
                .filter(x -> tagNo.equalsIgnoreCase(x.getTagNo()))
                .findFirst()
                .orElseGet(() -> {
                    JewelleryTag x = new JewelleryTag();
                    x.setCompany(company);
                    x.setBranch(branch);
                    x.setTagNo(tagNo);
                    x.setBarcode(barcode);
                    x.setStatus("AVAILABLE");
                    return tags.save(x);
                });

        boolean itemExists = items.findByCompanyId(company.getId()).stream()
                .anyMatch(x -> x.getTag() != null && Objects.equals(x.getTag().getId(), tag.getId()));

        if (!itemExists) {
            JewelleryItem item = new JewelleryItem();
            item.setCompany(company);
            item.setBranch(branch);
            item.setProduct(product);
            item.setTag(tag);
            item.setPurity(purity);
            item.setGrossWeight(gross);
            item.setStoneWeight(stone);
            item.setNetWeight(net);
            item.setWastagePercent(new BigDecimal("8.000"));
            item.setMakingCharge(new BigDecimal("500.000"));
            item.setStoneValue(BigDecimal.ZERO);
            item.setStatus("IN_STOCK");
            items.save(item);
        }
    }
}
