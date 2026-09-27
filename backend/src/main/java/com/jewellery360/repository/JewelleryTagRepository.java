package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JewelleryTagRepository extends JpaRepository<JewelleryTag, Long> {
    List<JewelleryTag> findByCompanyId(Long companyId);
    Optional<JewelleryTag> findByCompanyIdAndBarcode(Long companyId, String barcode);
    Optional<JewelleryTag> findByCompanyIdAndTagNo(Long companyId, String tagNo);
}
