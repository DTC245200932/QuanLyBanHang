package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.PriceListItemRequest;
import com.quanlybanhang.dto.request.PriceListNewVersionRequest;
import com.quanlybanhang.dto.request.PriceListRequest;
import com.quanlybanhang.dto.response.CustomerGroupResponse;
import com.quanlybanhang.dto.response.PriceListItemResponse;
import com.quanlybanhang.dto.response.PriceListResponse;
import com.quanlybanhang.exception.PriceListLockedException;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.PriceList;
import com.quanlybanhang.model.PriceListItem;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.OrderRepository;
import com.quanlybanhang.repository.PriceListItemRepository;
import com.quanlybanhang.repository.PriceListRepository;
import com.quanlybanhang.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceListServiceImpl implements PriceListService {

    private final PriceListRepository priceListRepository;
    private final PriceListItemRepository priceListItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<PriceListResponse> getAllPriceLists(CustomerGroup customerGroup, Boolean active, String keyword) {
        List<PriceList> list = priceListRepository.findAllByOrderByCreatedAtDesc();

        return list.stream()
                .filter(p -> customerGroup == null || p.getCustomerGroup() == customerGroup)
                .filter(p -> active == null || Objects.equals(p.getActive(), active))
                .filter(p -> {
                    if (keyword == null || keyword.isBlank()) return true;
                    String kw = keyword.trim().toLowerCase();
                    return (p.getCode() != null && p.getCode().toLowerCase().contains(kw)) ||
                            (p.getName() != null && p.getName().toLowerCase().contains(kw));
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListResponse getPriceListById(Long id) {
        PriceList priceList = priceListRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng giá với ID: " + id));
        return mapToResponse(priceList);
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListResponse getEffectivePriceList(CustomerGroup customerGroup, LocalDate date) {
        if (customerGroup == null) {
            throw new IllegalArgumentException("Nhóm khách hàng không được để trống");
        }
        LocalDate queryDate = date != null ? date : LocalDate.now();
        List<PriceList> effectiveList = priceListRepository.findEffectivePriceLists(customerGroup, queryDate);
        if (effectiveList.isEmpty()) {
            return null;
        }
        return mapToResponse(effectiveList.get(0));
    }

    @Override
    @Transactional
    public PriceListResponse createPriceList(PriceListRequest request, String username) {
        validateDateRange(request.getStartDate(), request.getEndDate());

        String code = request.getCode().trim();
        if (priceListRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Mã bảng giá '" + code + "' đã tồn tại");
        }

        PriceList priceList = PriceList.builder()
                .code(code)
                .name(request.getName().trim())
                .customerGroup(request.getCustomerGroup())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(request.getActive() != null ? request.getActive() : true)
                .version(1)
                .hasOrders(false)
                .description(request.getDescription())
                .createdBy(username != null ? username : "system")
                .items(new ArrayList<>())
                .build();

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            Set<Long> productIds = new HashSet<>();
            for (PriceListItemRequest itemReq : request.getItems()) {
                Product product = resolveProduct(itemReq);
                if (!productIds.add(product.getId())) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getName() + "' bị trùng lặp trong bảng giá");
                }
                validateItemPrices(product, itemReq);

                PriceListItem item = PriceListItem.builder()
                        .priceList(priceList)
                        .product(product)
                        .sellingPrice(itemReq.getSellingPrice())
                        .floorPrice(itemReq.getFloorPrice())
                        .note(itemReq.getNote())
                        .build();

                priceList.addItem(item);
            }
        }

        PriceList saved = priceListRepository.save(priceList);
        auditLogService.logAction("PRICELIST", saved.getCode(), "CREATE", null,
                "Tạo bảng giá cho " + saved.getCustomerGroup().getDisplayName(),
                username != null ? username : "system");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public PriceListResponse updatePriceList(Long id, PriceListRequest request, String username) {
        PriceList priceList = priceListRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng giá với ID: " + id));

        // QUY TẮC NGHIỆP VỤ: Bảng giá đã phát sinh đơn thì không sửa, chỉ tạo phiên bản mới
        boolean hasOrders = Boolean.TRUE.equals(priceList.getHasOrders()) || orderRepository.existsByPriceListId(id);
        if (hasOrders) {
            throw new PriceListLockedException("Bảng giá đã phát sinh đơn hàng, không được phép chỉnh sửa. Vui lòng tạo phiên bản mới.");
        }

        validateDateRange(request.getStartDate(), request.getEndDate());

        String newCode = request.getCode().trim();
        if (priceListRepository.existsByCodeAndIdNot(newCode, id)) {
            throw new IllegalArgumentException("Mã bảng giá '" + newCode + "' đã tồn tại");
        }

        priceList.setCode(newCode);
        priceList.setName(request.getName().trim());
        priceList.setCustomerGroup(request.getCustomerGroup());
        priceList.setStartDate(request.getStartDate());
        priceList.setEndDate(request.getEndDate());
        if (request.getActive() != null) {
            priceList.setActive(request.getActive());
        }
        priceList.setDescription(request.getDescription());

        // Cập nhật danh sách dòng giá
        priceList.getItems().clear();
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            Set<Long> productIds = new HashSet<>();
            for (PriceListItemRequest itemReq : request.getItems()) {
                Product product = resolveProduct(itemReq);
                if (!productIds.add(product.getId())) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getName() + "' bị trùng lặp trong bảng giá");
                }
                validateItemPrices(product, itemReq);

                PriceListItem item = PriceListItem.builder()
                        .priceList(priceList)
                        .product(product)
                        .sellingPrice(itemReq.getSellingPrice())
                        .floorPrice(itemReq.getFloorPrice())
                        .note(itemReq.getNote())
                        .build();

                priceList.addItem(item);
            }
        }

        PriceList saved = priceListRepository.save(priceList);
        auditLogService.logAction("PRICELIST", saved.getCode(), "UPDATE", null,
                "Cập nhật bảng giá", username != null ? username : "system");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deletePriceList(Long id, String username) {
        PriceList priceList = priceListRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng giá với ID: " + id));

        // QUY TẮC NGHIỆP VỤ: Bảng giá đã phát sinh đơn thì không xóa
        boolean hasOrders = Boolean.TRUE.equals(priceList.getHasOrders()) || orderRepository.existsByPriceListId(id);
        if (hasOrders) {
            throw new PriceListLockedException("Bảng giá đã phát sinh đơn hàng, không được phép xóa.");
        }

        priceListRepository.delete(priceList);
        auditLogService.logAction("PRICELIST", priceList.getCode(), "DELETE", null,
                "Xóa bảng giá", username != null ? username : "system");
    }

    @Override
    @Transactional
    public PriceListResponse createNewVersion(Long id, PriceListNewVersionRequest request, String username) {
        PriceList parent = priceListRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng giá gốc với ID: " + id));

        int nextVersion = (parent.getVersion() != null ? parent.getVersion() : 1) + 1;

        String code = request != null && request.getNewCode() != null && !request.getNewCode().isBlank()
                ? request.getNewCode().trim()
                : generateNextVersionCode(parent.getCode(), nextVersion);

        if (priceListRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Mã bảng giá '" + code + "' đã tồn tại");
        }

        String name = request != null && request.getNewName() != null && !request.getNewName().isBlank()
                ? request.getNewName().trim()
                : parent.getName() + " (v" + nextVersion + ")";

        LocalDate startDate = request != null && request.getStartDate() != null
                ? request.getStartDate()
                : parent.getStartDate();

        LocalDate endDate = request != null && request.getEndDate() != null
                ? request.getEndDate()
                : parent.getEndDate();

        validateDateRange(startDate, endDate);

        PriceList newVersion = PriceList.builder()
                .code(code)
                .name(name)
                .customerGroup(parent.getCustomerGroup())
                .startDate(startDate)
                .endDate(endDate)
                .active(true)
                .version(nextVersion)
                .parentPriceList(parent)
                .hasOrders(false) // Phiên bản mới chưa phát sinh đơn hàng
                .description(request != null && request.getDescription() != null
                        ? request.getDescription()
                        : "Kế thừa từ phiên bản v" + parent.getVersion() + " (" + parent.getCode() + ")")
                .createdBy(username != null ? username : "system")
                .items(new ArrayList<>())
                .build();

        // Nếu request có truyền dòng giá mới thì dùng, nếu không thì copy toàn bộ từ bản cũ
        if (request != null && request.getItems() != null && !request.getItems().isEmpty()) {
            Set<Long> productIds = new HashSet<>();
            for (PriceListItemRequest itemReq : request.getItems()) {
                Product product = resolveProduct(itemReq);
                if (!productIds.add(product.getId())) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getName() + "' bị trùng lặp trong bảng giá");
                }
                validateItemPrices(product, itemReq);

                PriceListItem item = PriceListItem.builder()
                        .priceList(newVersion)
                        .product(product)
                        .sellingPrice(itemReq.getSellingPrice())
                        .floorPrice(itemReq.getFloorPrice())
                        .note(itemReq.getNote())
                        .build();

                newVersion.addItem(item);
            }
        } else {
            // Sao chép toàn bộ dòng giá từ bảng giá cha
            for (PriceListItem oldItem : parent.getItems()) {
                PriceListItem newItem = PriceListItem.builder()
                        .priceList(newVersion)
                        .product(oldItem.getProduct())
                        .sellingPrice(oldItem.getSellingPrice())
                        .floorPrice(oldItem.getFloorPrice())
                        .note(oldItem.getNote())
                        .build();
                newVersion.addItem(newItem);
            }
        }

        PriceList saved = priceListRepository.save(newVersion);
        auditLogService.logAction("PRICELIST", saved.getCode(), "NEW_VERSION",
                parent.getCode(), "Tạo phiên bản v" + nextVersion,
                username != null ? username : "system");

        return mapToResponse(saved);
    }

    @Override
    public List<CustomerGroupResponse> getCustomerGroups() {
        return Arrays.stream(CustomerGroup.values())
                .map(cg -> CustomerGroupResponse.builder()
                        .code(cg.name())
                        .name(cg.getDisplayName())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public PriceListResponse mapToResponse(PriceList priceList) {
        List<PriceListItemResponse> itemResponses = priceList.getItems() != null
                ? priceList.getItems().stream().map(this::mapToItemResponse).collect(Collectors.toList())
                : Collections.emptyList();

        boolean hasOrders = Boolean.TRUE.equals(priceList.getHasOrders())
                || (priceList.getId() != null && orderRepository.existsByPriceListId(priceList.getId()));

        return PriceListResponse.builder()
                .id(priceList.getId())
                .code(priceList.getCode())
                .name(priceList.getName())
                .customerGroup(priceList.getCustomerGroup())
                .customerGroupName(priceList.getCustomerGroup() != null ? priceList.getCustomerGroup().getDisplayName() : null)
                .startDate(priceList.getStartDate())
                .endDate(priceList.getEndDate())
                .version(priceList.getVersion())
                .active(priceList.getActive())
                .hasOrders(hasOrders)
                .description(priceList.getDescription())
                .parentPriceListId(priceList.getParentPriceList() != null ? priceList.getParentPriceList().getId() : null)
                .createdBy(priceList.getCreatedBy())
                .createdAt(priceList.getCreatedAt())
                .updatedAt(priceList.getUpdatedAt())
                .items(itemResponses)
                .build();
    }

    private PriceListItemResponse mapToItemResponse(PriceListItem item) {
        Product p = item.getProduct();
        return PriceListItemResponse.builder()
                .id(item.getId())
                .productId(p != null ? p.getId() : null)
                .productCode(p != null ? p.getCode() : null)
                .productName(p != null ? p.getName() : null)
                .unit(p != null ? p.getUnit() : null)
                .sellingPrice(item.getSellingPrice())
                .floorPrice(item.getFloorPrice())
                .note(item.getNote())
                .build();
    }

    private Product resolveProduct(PriceListItemRequest itemReq) {
        if (itemReq.getProductId() != null) {
            return productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));
        }
        if (itemReq.getProductCode() != null && !itemReq.getProductCode().isBlank()) {
            return productRepository.findByCode(itemReq.getProductCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với mã: " + itemReq.getProductCode()));
        }
        throw new IllegalArgumentException("Cần cung cấp productId hoặc productCode cho từng dòng giá");
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và ngày kết thúc không được để trống");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Ngày bắt đầu hiệu lực không được sau ngày kết thúc hiệu lực");
        }
    }

    private void validateItemPrices(Product product, PriceListItemRequest itemReq) {
        if (itemReq.getSellingPrice() == null || itemReq.getFloorPrice() == null) {
            throw new IllegalArgumentException("Giá bán và giá sàn không được để trống cho sản phẩm: " + product.getName());
        }
        if (itemReq.getFloorPrice().compareTo(itemReq.getSellingPrice()) > 0) {
            throw new IllegalArgumentException("Giá sàn không được lớn hơn giá bán đối với sản phẩm '" + product.getName() + "'");
        }
    }

    private String generateNextVersionCode(String currentCode, int nextVersion) {
        if (currentCode.matches(".*-V\\d+$")) {
            return currentCode.replaceAll("-V\\d+$", "-V" + nextVersion);
        }
        return currentCode + "-V" + nextVersion;
    }
}
