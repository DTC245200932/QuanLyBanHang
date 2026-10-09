package com.quanlybanhang.repository;

import com.quanlybanhang.model.VolumeDiscountTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VolumeDiscountTierRepository extends JpaRepository<VolumeDiscountTier, Long> {

    List<VolumeDiscountTier> findByPolicyIdOrderByMinQuantityAsc(Long policyId);

    void deleteByPolicyId(Long policyId);
}

