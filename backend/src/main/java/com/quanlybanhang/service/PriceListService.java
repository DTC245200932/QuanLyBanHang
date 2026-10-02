package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.PriceListNewVersionRequest;
import com.quanlybanhang.dto.request.PriceListRequest;
import com.quanlybanhang.dto.response.CustomerGroupResponse;
import com.quanlybanhang.dto.response.PriceListResponse;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.PriceList;

import java.time.LocalDate;
import java.util.List;

public interface PriceListService {

    List<PriceListResponse> getAllPriceLists(CustomerGroup customerGroup, Boolean active, String keyword);

    PriceListResponse getPriceListById(Long id);

    PriceListResponse getEffectivePriceList(CustomerGroup customerGroup, LocalDate date);

    PriceListResponse createPriceList(PriceListRequest request, String username);

    PriceListResponse updatePriceList(Long id, PriceListRequest request, String username);

    void deletePriceList(Long id, String username);

    PriceListResponse createNewVersion(Long id, PriceListNewVersionRequest request, String username);

    List<CustomerGroupResponse> getCustomerGroups();

    PriceListResponse mapToResponse(PriceList priceList);
}
