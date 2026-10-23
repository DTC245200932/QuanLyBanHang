package com.quanlybanhang.dto.response;

/** Minimal dealer data for S5-03; never exposes financial/customer profile fields. */
public record WarehouseDealerResponse(Long id, String code, String name, Boolean active,
        Long warehouseId, String warehouseCode, String warehouseName) { }
