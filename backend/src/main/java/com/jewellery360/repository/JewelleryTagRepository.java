package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryTag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JewelleryTagRepository extends JpaRepository<JewelleryTag, Long> {
    @EntityGraph(attributePaths = {"company", "branch"})
List<JewelleryTag> findByCompanyId(Long companyId);
    Optional<JewelleryTag> findByCompanyIdAndBarcode(Long companyId, String barcode);
    Optional<JewelleryTag> findByCompanyIdAndTagNo(Long companyId, String tagNo);
    @Override
    @EntityGraph(attributePaths = {"company", "branch"})
    Optional<JewelleryTag> findById(Long id);

    boolean existsByCompanyIdAndTagNoIgnoreCase(Long companyId, String tagNo);
    boolean existsByCompanyIdAndBarcode(Long companyId, String barcode);
}
