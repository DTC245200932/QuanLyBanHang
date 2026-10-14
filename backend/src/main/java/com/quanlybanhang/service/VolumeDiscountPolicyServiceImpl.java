package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.VolumeDiscountCalculateItemRequest;
import com.quanlybanhang.dto.request.VolumeDiscountCalculateRequest;
import com.quanlybanhang.dto.request.VolumeDiscountPolicyRequest;
import com.quanlybanhang.dto.request.VolumeDiscountTierRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.PriceListRepository;
import com.quanlybanhang.repository.ProductRepository;
import com.quanlybanhang.repository.VolumeDiscountPolicyRepository;
import com.quanlybanhang.repository.VolumeDiscountTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cài đặt nghiệp vụ Chính sách chiết khấu theo sản lượng (S3-01).
 *
 * Quy tắc nghiệp vụ cốt lõi:
 * 1. Khai báo bậc chiết khấu theo số lượng mua của một SKU hoặc một nhóm hàng.
 * 2. Chiết khấu theo % hoặc theo số tiền trên một đơn vị cơ sở.
 * 3. Khi nhiều chính sách cùng áp dụng, hệ thống tự động so sánh và chọn chính sách có lợi nhất
 *    cho khách (mức chiết khấu bằng tiền lớn nhất).
 */
@Service
@RequiredArgsConstructor
public class VolumeDiscountPolicyServiceImpl implements VolumeDiscountPolicyService {

    private final VolumeDiscountPolicyRepository policyRepository;
    private final VolumeDiscountTierRepository tierRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final DealerRepository dealerRepository;
    private final PriceListRepository priceListRepository;
    private final UnitConversionService unitConversionService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public VolumeDiscountPolicyResponse createPolicy(VolumeDiscountPolicyRequest request, String username) {
        validatePolicyRequest(request, null);

        Product product = null;
        Category category = null;

        if (request.getTargetType() == DiscountTargetType.PRODUCT) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + request.getProductId()));
        } else if (request.getTargetType() == DiscountTargetType.CATEGORY) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + request.getCategoryId()));
        }

        VolumeDiscountPolicy policy = VolumeDiscountPolicy.builder()
                .code(request.getCode().trim().toUpperCase())
                .name(request.getName().trim())
                .description(request.getDescription())
                .targetType(request.getTargetType())
                .product(product)
                .category(category)
                .customerGroup(request.getCustomerGroup())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(request.getActive() != null ? request.getActive() : true)
                .createdBy(username != null ? username : "system")
                .tiers(new ArrayList<>())
                .build();

        addTiersToPolicy(policy, request.getTiers());

        VolumeDiscountPolicy saved = policyRepository.save(policy);

        auditLogService.logAction(
                "DISCOUNT_POLICY",
                saved.getCode(),
                "CREATE",
                null,
                String.format("Tạo chính sách chiết khấu: %s (%s)", saved.getName(), saved.getTargetType()),
                username != null ? username : "system"
        );

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public VolumeDiscountPolicyResponse updatePolicy(Long id, VolumeDiscountPolicyRequest request, String username) {
        VolumeDiscountPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chính sách chiết khấu với ID: " + id));

        validatePolicyRequest(request, id);

        Product product = null;
        Category category = null;

        if (request.getTargetType() == DiscountTargetType.PRODUCT) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + request.getProductId()));
        } else if (request.getTargetType() == DiscountTargetType.CATEGORY) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + request.getCategoryId()));
        }

        String oldSummary = String.format("Mã: %s, Tên: %s, Target: %s", policy.getCode(), policy.getName(), policy.getTargetType());

        policy.setCode(request.getCode().trim().toUpperCase());
        policy.setName(request.getName().trim());
        policy.setDescription(request.getDescription());
        policy.setTargetType(request.getTargetType());
        policy.setProduct(product);
        policy.setCategory(category);
        policy.setCustomerGroup(request.getCustomerGroup());
        policy.setStartDate(request.getStartDate());
        policy.setEndDate(request.getEndDate());
        if (request.getActive() != null) {
            policy.setActive(request.getActive());
        }

        // Cập nhật lại các bậc chiết khấu
        policy.clearTiers();
        addTiersToPolicy(policy, request.getTiers());

        VolumeDiscountPolicy saved = policyRepository.save(policy);

        String newSummary = String.format("Mã: %s, Tên: %s, Target: %s", saved.getCode(), saved.getName(), saved.getTargetType());
        auditLogService.logAction(
                "DISCOUNT_POLICY",
                saved.getCode(),
                "UPDATE",
                oldSummary,
                newSummary,
                username != null ? username : "system"
        );

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deletePolicy(Long id, String username) {
        VolumeDiscountPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chính sách chiết khấu với ID: " + id));

        auditLogService.logAction(
                "DISCOUNT_POLICY",
                policy.getCode(),
                "DELETE",
                policy.getName(),
                null,
                username != null ? username : "system"
        );

        policyRepository.delete(policy);
    }

    @Override
    @Transactional
    public VolumeDiscountPolicyResponse toggleActive(Long id, Boolean active, String username) {
        VolumeDiscountPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chính sách chiết khấu với ID: " + id));

        Boolean oldActive = policy.getActive();
        policy.setActive(active != null ? active : !Boolean.TRUE.equals(policy.getActive()));

        VolumeDiscountPolicy saved = policyRepository.save(policy);

        auditLogService.logAction(
                "DISCOUNT_POLICY",
                saved.getCode(),
                "STATUS",
                String.valueOf(oldActive),
                String.valueOf(saved.getActive()),
                username != null ? username : "system"
        );

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VolumeDiscountPolicyResponse getPolicyById(Long id) {
        VolumeDiscountPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chính sách chiết khấu với ID: " + id));
        return mapToResponse(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VolumeDiscountPolicyResponse> getAllPolicies(
            DiscountTargetType targetType,
            Long productId,
            Long categoryId,
            CustomerGroup customerGroup,
            Boolean active,
            String keyword) {

        List<VolumeDiscountPolicy> all = policyRepository.findAll();

        return all.stream()
                .filter(p -> targetType == null || p.getTargetType() == targetType)
                .filter(p -> productId == null || (p.getProduct() != null && p.getProduct().getId().equals(productId)))
                .filter(p -> categoryId == null || (p.getCategory() != null && p.getCategory().getId().equals(categoryId)))
                .filter(p -> customerGroup == null || p.getCustomerGroup() == null || p.getCustomerGroup() == customerGroup)
                .filter(p -> active == null || Objects.equals(p.getActive(), active))
                .filter(p -> {
                    if (keyword == null || keyword.isBlank()) return true;
                    String kw = keyword.trim().toLowerCase();
                    return p.getCode().toLowerCase().contains(kw) || p.getName().toLowerCase().contains(kw);
                })
                .sorted(Comparator.comparing(VolumeDiscountPolicy::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public VolumeDiscountCalculationResponse calculateDiscounts(VolumeDiscountCalculateRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Danh sách mặt hàng cần tính chiết khấu không được để trống");
        }

        CustomerGroup customerGroup = request.getCustomerGroup();
        if (customerGroup == null && request.getDealerId() != null) {
            Dealer dealer = dealerRepository.findById(request.getDealerId()).orElse(null);
            if (dealer != null) {
                customerGroup = dealer.getCustomerGroup();
            }
        }

        LocalDate orderDate = request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now();

        PriceList priceList = null;
        if (customerGroup != null && priceListRepository != null) {
            List<PriceList> effectiveLists = priceListRepository.findEffectivePriceLists(customerGroup, orderDate);
            if (!effectiveLists.isEmpty()) {
                priceList = effectiveLists.get(0);
            }
        }

        Map<Long, PriceListItem> priceItemMap = new HashMap<>();
        if (priceList != null && priceList.getItems() != null) {
            for (PriceListItem pli : priceList.getItems()) {
                if (pli.getProduct() != null) {
                    priceItemMap.put(pli.getProduct().getId(), pli);
                }
            }
        }

        // Chuẩn bị danh sách OrderItemDiscountInput
        List<OrderItemDiscountInput> inputs = new ArrayList<>();
        List<BigDecimal> itemPriceListSellingList = new ArrayList<>();
        List<BigDecimal> itemFloorPriceList = new ArrayList<>();

        for (int i = 0; i < request.getItems().size(); i++) {
            VolumeDiscountCalculateItemRequest itemReq = request.getItems().get(i);
            Product product = resolveProduct(itemReq);

            String unit = itemReq.getUnit();
            if (unit == null || unit.isBlank()) {
                unit = product.getUnit();
            } else {
                unit = unit.trim();
            }

            BigDecimal factor = unitConversionService.getConversionFactor(product, unit);
            BigDecimal baseQty = itemReq.getQuantity().multiply(factor);

            PriceListItem priceListItem = priceItemMap.get(product.getId());
            BigDecimal priceListSelling = null;
            BigDecimal floorPrice = null;
            if (priceListItem != null) {
                priceListSelling = priceListItem.getSellingPrice().multiply(factor);
                floorPrice = priceListItem.getFloorPrice().multiply(factor);
            }
            itemPriceListSellingList.add(priceListSelling);
            itemFloorPriceList.add(floorPrice);

            BigDecimal price = itemReq.getPrice();
            if (price == null) {
                if (priceListSelling != null) {
                    price = priceListSelling;
                } else {
                    price = product.getPrice() != null ? product.getPrice().multiply(factor) : BigDecimal.ZERO;
                }
            }

            BigDecimal subtotal = price.multiply(itemReq.getQuantity());

            inputs.add(OrderItemDiscountInput.builder()
                    .index(i)
                    .product(product)
                    .unit(unit)
                    .quantity(itemReq.getQuantity())
                    .conversionFactor(factor)
                    .baseQuantity(baseQty)
                    .baseUnit(product.getUnit())
                    .price(price)
                    .subtotalAmount(subtotal)
                    .build());
        }

        OrderDiscountSummary summary = calculateOrderDiscount(inputs, customerGroup, orderDate);

        List<VolumeDiscountItemCalculationResponse> itemResponses = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            OrderItemDiscountInput input = inputs.get(i);
            ItemDiscountResult itemResult = summary.getItemResults().get(input.getIndex());
            BigDecimal itemDiscount = itemResult != null ? itemResult.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal itemTotal = input.getSubtotalAmount().subtract(itemDiscount);

            BigDecimal floorPrice = itemFloorPriceList.get(i);
            BigDecimal priceListSelling = itemPriceListSellingList.get(i);
            boolean belowFloor = floorPrice != null && input.getPrice().compareTo(floorPrice) < 0;

            itemResponses.add(VolumeDiscountItemCalculationResponse.builder()
                    .productId(input.getProduct().getId())
                    .productCode(input.getProduct().getCode())
                    .productName(input.getProduct().getName())
                    .categoryId(input.getProduct().getCategory() != null ? input.getProduct().getCategory().getId() : null)
                    .categoryName(input.getProduct().getCategory() != null ? input.getProduct().getCategory().getName() : null)
                    .unit(input.getUnit())
                    .quantity(input.getQuantity())
                    .conversionFactor(input.getConversionFactor())
                    .baseUnit(input.getBaseUnit())
                    .baseQuantity(input.getBaseQuantity())
                    .price(input.getPrice())
                    .priceListSellingPrice(priceListSelling)
                    .floorPrice(floorPrice)
                    .belowFloorPrice(belowFloor)
                    .subtotalAmount(input.getSubtotalAmount())
                    .discountAmount(itemDiscount)
                    .discountPercent(itemResult != null ? itemResult.getDiscountPercent() : null)
                    .totalAmount(itemTotal)
                    .appliedPolicyId(itemResult != null ? itemResult.getAppliedPolicyId() : null)
                    .appliedPolicyCode(itemResult != null ? itemResult.getAppliedPolicyCode() : null)
                    .appliedPolicyName(itemResult != null ? itemResult.getAppliedPolicyName() : null)
                    .selectionReason(itemResult != null ? itemResult.getBestPolicyReason() : null)
                    .candidatePolicies(itemResult != null && itemResult.getCandidatePolicies() != null
                            ? itemResult.getCandidatePolicies() : Collections.emptyList())
                    .build());
        }

        return VolumeDiscountCalculationResponse.builder()
                .subtotalAmount(summary.getSubtotalAmount())
                .discountAmount(summary.getDiscountAmount())
                .totalAmount(summary.getTotalAmount())
                .items(itemResponses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDiscountSummary calculateOrderDiscount(
            List<OrderItemDiscountInput> items,
            CustomerGroup customerGroup,
            LocalDate orderDate) {

        if (items == null || items.isEmpty()) {
            return OrderDiscountSummary.builder()
                    .subtotalAmount(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.ZERO)
                    .itemResults(Collections.emptyMap())
                    .build();
        }

        if (orderDate == null) {
            orderDate = LocalDate.now();
        }

        // 1. Lấy tất cả các chính sách đang hoạt động và có hiệu lực thời gian
        List<VolumeDiscountPolicy> activePolicies = policyRepository.findActivePoliciesByDate(orderDate);

        // Lọc theo nhóm khách hàng (policy.customerGroup == null áp dụng cho mọi khách)
        List<VolumeDiscountPolicy> applicablePolicies = activePolicies.stream()
                .filter(p -> p.getCustomerGroup() == null || customerGroup == null || p.getCustomerGroup() == customerGroup)
                .collect(Collectors.toList());

        // 2. Tổng hợp sản lượng mua trên toàn đơn hàng (theo đơn vị cơ sở)
        // a) Tổng sản lượng theo từng sản phẩm (SKU)
        Map<Long, BigDecimal> productBaseQtyMap = new HashMap<>();
        // b) Tổng sản lượng theo từng nhóm hàng (bao gồm mọi sản phẩm thuộc cây con của nhóm hàng đó)
        Map<Long, BigDecimal> categoryBaseQtyMap = new HashMap<>();

        for (OrderItemDiscountInput item : items) {
            if (item.getProduct() == null || item.getBaseQuantity() == null) continue;
            Long prodId = item.getProduct().getId();
            productBaseQtyMap.merge(prodId, item.getBaseQuantity(), BigDecimal::add);

            Category cat = item.getProduct().getCategory();
            while (cat != null) {
                if (cat.getId() != null) {
                    categoryBaseQtyMap.merge(cat.getId(), item.getBaseQuantity(), BigDecimal::add);
                }
                cat = cat.getParent();
            }
        }

        // 3. Tính toán chiết khấu cho từng dòng mặt hàng
        BigDecimal grandSubtotal = BigDecimal.ZERO;
        BigDecimal grandDiscount = BigDecimal.ZERO;
        Map<Integer, ItemDiscountResult> resultMap = new HashMap<>();

        for (OrderItemDiscountInput item : items) {
            BigDecimal subtotal = item.getSubtotalAmount() != null
                    ? item.getSubtotalAmount()
                    : item.getPrice().multiply(item.getQuantity());
            grandSubtotal = grandSubtotal.add(subtotal);

            // Tìm tất cả các chính sách ứng viên thỏa mãn cho dòng này
            List<CandidateDiscountPolicyInfo> candidates = new ArrayList<>();

            for (VolumeDiscountPolicy policy : applicablePolicies) {
                BigDecimal qualifyingQty = null;

                if (policy.getTargetType() == DiscountTargetType.PRODUCT) {
                    if (policy.getProduct() != null && policy.getProduct().getId().equals(item.getProduct().getId())) {
                        qualifyingQty = productBaseQtyMap.get(item.getProduct().getId());
                    }
                } else if (policy.getTargetType() == DiscountTargetType.CATEGORY) {
                    if (policy.getCategory() != null && isProductInCategory(item.getProduct(), policy.getCategory().getId())) {
                        qualifyingQty = categoryBaseQtyMap.get(policy.getCategory().getId());
                    }
                }

                if (qualifyingQty == null || qualifyingQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // Tìm bậc chiết khấu phù hợp với sản lượng
                VolumeDiscountTier matchedTier = findMatchingTier(policy.getTiers(), qualifyingQty);
                if (matchedTier != null) {
                    BigDecimal calcDiscountAmount = BigDecimal.ZERO;
                    BigDecimal calcDiscountPercent = BigDecimal.ZERO;

                    if (matchedTier.getDiscountType() == DiscountType.PERCENTAGE) {
                        calcDiscountPercent = matchedTier.getDiscountValue();
                        calcDiscountAmount = subtotal.multiply(calcDiscountPercent)
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    } else if (matchedTier.getDiscountType() == DiscountType.FIXED_AMOUNT) {
                        // Chiết khấu cố định trên một đơn vị cơ sở
                        calcDiscountAmount = matchedTier.getDiscountValue()
                                .multiply(item.getBaseQuantity())
                                .setScale(2, RoundingMode.HALF_UP);
                        if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
                            calcDiscountPercent = calcDiscountAmount.multiply(BigDecimal.valueOf(100))
                                    .divide(subtotal, 2, RoundingMode.HALF_UP);
                        }
                    }

                    // Không vượt quá thành tiền của dòng
                    calcDiscountAmount = calcDiscountAmount.min(subtotal).max(BigDecimal.ZERO);

                    candidates.add(CandidateDiscountPolicyInfo.builder()
                            .policyId(policy.getId())
                            .policyCode(policy.getCode())
                            .policyName(policy.getName())
                            .targetType(policy.getTargetType())
                            .matchedTierId(matchedTier.getId())
                            .minQuantity(matchedTier.getMinQuantity())
                            .maxQuantity(matchedTier.getMaxQuantity())
                            .discountType(matchedTier.getDiscountType())
                            .discountValue(matchedTier.getDiscountValue())
                            .calculatedDiscountAmount(calcDiscountAmount)
                            .calculatedDiscountPercent(calcDiscountPercent)
                            .isSelected(false)
                            .build());
                }
            }

            // 4. QUY TẮC CỐT LÕI: Nhiều chính sách cùng áp dụng thì lấy chính sách có lợi nhất cho khách (chiết khấu cao nhất)
            if (candidates.isEmpty()) {
                resultMap.put(item.getIndex(), ItemDiscountResult.builder()
                        .discountAmount(BigDecimal.ZERO)
                        .discountPercent(null)
                        .appliedPolicyId(null)
                        .appliedPolicyCode(null)
                        .appliedPolicyName(null)
                        .bestPolicyReason("Không có chính sách chiết khấu nào áp dụng cho sản lượng hiện tại")
                        .candidatePolicies(Collections.emptyList())
                        .build());
            } else {
                // Sắp xếp các chính sách ứng viên theo số tiền chiết khấu giảm dần
                // Nếu bằng tiền, ưu tiên chính sách trực tiếp theo SKU trước chính sách nhóm hàng, sau đó theo %
                candidates.sort((c1, c2) -> {
                    int cmp = c2.getCalculatedDiscountAmount().compareTo(c1.getCalculatedDiscountAmount());
                    if (cmp != 0) return cmp;
                    if (c1.getTargetType() == DiscountTargetType.PRODUCT && c2.getTargetType() != DiscountTargetType.PRODUCT) {
                        return -1;
                    }
                    if (c2.getTargetType() == DiscountTargetType.PRODUCT && c1.getTargetType() != DiscountTargetType.PRODUCT) {
                        return 1;
                    }
                    return c2.getCalculatedDiscountPercent().compareTo(c1.getCalculatedDiscountPercent());
                });

                CandidateDiscountPolicyInfo best = candidates.get(0);
                best.setIsSelected(true);

                String reason;
                if (candidates.size() == 1) {
                    reason = String.format("Áp dụng chính sách '%s' (%s): giảm %s",
                            best.getPolicyCode(),
                            best.getTargetType() == DiscountTargetType.PRODUCT ? "theo SKU" : "theo nhóm hàng",
                            formatDiscountTierValue(best));
                } else {
                    reason = String.format("Áp dụng chính sách có lợi nhất cho khách '%s' (%s: %s đ), vượt trội hơn %d chính sách khác (quy tắc chọn mức giảm cao nhất)",
                            best.getPolicyCode(),
                            formatDiscountTierValue(best),
                            best.getCalculatedDiscountAmount().toPlainString(),
                            candidates.size() - 1);
                }

                grandDiscount = grandDiscount.add(best.getCalculatedDiscountAmount());

                resultMap.put(item.getIndex(), ItemDiscountResult.builder()
                        .discountAmount(best.getCalculatedDiscountAmount())
                        .discountPercent(best.getCalculatedDiscountPercent())
                        .appliedPolicyId(best.getPolicyId())
                        .appliedPolicyCode(best.getPolicyCode())
                        .appliedPolicyName(best.getPolicyName())
                        .bestPolicyReason(reason)
                        .candidatePolicies(candidates)
                        .build());
            }
        }

        BigDecimal grandTotal = grandSubtotal.subtract(grandDiscount).max(BigDecimal.ZERO);

        return OrderDiscountSummary.builder()
                .subtotalAmount(grandSubtotal)
                .discountAmount(grandDiscount)
                .totalAmount(grandTotal)
                .itemResults(resultMap)
                .build();
    }

    private boolean isProductInCategory(Product product, Long targetCategoryId) {
        if (product == null || product.getCategory() == null || targetCategoryId == null) {
            return false;
        }
        Category current = product.getCategory();
        while (current != null) {
            if (targetCategoryId.equals(current.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private VolumeDiscountTier findMatchingTier(List<VolumeDiscountTier> tiers, BigDecimal qty) {
        if (tiers == null || tiers.isEmpty() || qty == null) {
            return null;
        }

        // Lọc các bậc thỏa mãn: qty >= minQuantity và (maxQuantity == null || qty <= maxQuantity)
        List<VolumeDiscountTier> matching = tiers.stream()
                .filter(t -> qty.compareTo(t.getMinQuantity()) >= 0)
                .filter(t -> t.getMaxQuantity() == null || qty.compareTo(t.getMaxQuantity()) <= 0)
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            return null;
        }

        // Chọn bậc có minQuantity cao nhất (bậc cao nhất mà khách đạt được)
        return matching.stream()
                .max(Comparator.comparing(VolumeDiscountTier::getMinQuantity))
                .orElse(null);
    }

    private String formatDiscountTierValue(CandidateDiscountPolicyInfo candidate) {
        if (candidate.getDiscountType() == DiscountType.PERCENTAGE) {
            return candidate.getDiscountValue().stripTrailingZeros().toPlainString() + "%";
        } else {
            return candidate.getDiscountValue().stripTrailingZeros().toPlainString() + " đ/đvcs";
        }
    }

    private void addTiersToPolicy(VolumeDiscountPolicy policy, List<VolumeDiscountTierRequest> tierRequests) {
        if (tierRequests == null || tierRequests.isEmpty()) {
            throw new IllegalArgumentException("Chính sách phải có ít nhất 1 bậc chiết khấu");
        }

        // Sắp xếp các bậc theo minQuantity tăng dần
        List<VolumeDiscountTierRequest> sorted = new ArrayList<>(tierRequests);
        sorted.sort(Comparator.comparing(VolumeDiscountTierRequest::getMinQuantity));

        for (int i = 0; i < sorted.size(); i++) {
            VolumeDiscountTierRequest req = sorted.get(i);

            if (req.getMinQuantity() == null || req.getMinQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Số lượng mua tối thiểu của mỗi bậc phải lớn hơn 0");
            }

            if (req.getMaxQuantity() != null && req.getMaxQuantity().compareTo(req.getMinQuantity()) < 0) {
                throw new IllegalArgumentException("Số lượng tối đa không được nhỏ hơn số lượng tối thiểu");
            }

            if (req.getDiscountType() == null) {
                throw new IllegalArgumentException("Loại chiết khấu không được để trống (PERCENTAGE hoặc FIXED_AMOUNT)");
            }

            if (req.getDiscountValue() == null || req.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Giá trị chiết khấu phải lớn hơn 0");
            }

            if (req.getDiscountType() == DiscountType.PERCENTAGE && req.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Chiết khấu phần trăm không được vượt quá 100%");
            }

            // Kiểm tra tính liên tục / không chồng lấn
            if (i > 0) {
                VolumeDiscountTierRequest prev = sorted.get(i - 1);
                if (prev.getMaxQuantity() == null) {
                    throw new IllegalArgumentException("Bậc chiết khấu không giới hạn số lượng tối đa phải là bậc cuối cùng");
                }
                if (req.getMinQuantity().compareTo(prev.getMaxQuantity()) <= 0) {
                    throw new IllegalArgumentException("Các bậc chiết khấu không được chồng lấn khoảng số lượng: bậc trước kết thúc ở "
                            + prev.getMaxQuantity() + ", bậc sau bắt đầu từ " + req.getMinQuantity());
                }
            }

            VolumeDiscountTier tier = VolumeDiscountTier.builder()
                    .policy(policy)
                    .minQuantity(req.getMinQuantity())
                    .maxQuantity(req.getMaxQuantity())
                    .discountType(req.getDiscountType())
                    .discountValue(req.getDiscountValue())
                    .tierOrder(i + 1)
                    .build();

            policy.addTier(tier);
        }
    }

    private void validatePolicyRequest(VolumeDiscountPolicyRequest request, Long currentId) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Mã chính sách chiết khấu không được để trống");
        }

        String code = request.getCode().trim().toUpperCase();
        if (currentId == null) {
            if (policyRepository.existsByCode(code)) {
                throw new IllegalArgumentException("Mã chính sách chiết khấu '" + code + "' đã tồn tại");
            }
        } else {
            if (policyRepository.existsByCodeAndIdNot(code, currentId)) {
                throw new IllegalArgumentException("Mã chính sách chiết khấu '" + code + "' đã được sử dụng");
            }
        }

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Tên chính sách chiết khấu không được để trống");
        }

        if (request.getTargetType() == null) {
            throw new IllegalArgumentException("Đối tượng áp dụng không được để trống (PRODUCT hoặc CATEGORY)");
        }

        if (request.getTargetType() == DiscountTargetType.PRODUCT && request.getProductId() == null) {
            throw new IllegalArgumentException("Khi áp dụng cho sản phẩm cụ thể, productId không được để trống");
        }

        if (request.getTargetType() == DiscountTargetType.CATEGORY && request.getCategoryId() == null) {
            throw new IllegalArgumentException("Khi áp dụng cho nhóm hàng, categoryId không được để trống");
        }

        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc");
            }
        }

        if (request.getTiers() == null || request.getTiers().isEmpty()) {
            throw new IllegalArgumentException("Chính sách phải có ít nhất 1 bậc chiết khấu");
        }
    }

    private Product resolveProduct(VolumeDiscountCalculateItemRequest itemReq) {
        if (itemReq.getProductId() != null) {
            return productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));
        }
        if (itemReq.getProductCode() != null && !itemReq.getProductCode().isBlank()) {
            return productRepository.findByCode(itemReq.getProductCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với mã: " + itemReq.getProductCode()));
        }
        throw new IllegalArgumentException("Cần cung cấp productId hoặc productCode cho từng sản phẩm");
    }

    private VolumeDiscountPolicyResponse mapToResponse(VolumeDiscountPolicy policy) {
        List<VolumeDiscountTierResponse> tierResponses = policy.getTiers() != null
                ? policy.getTiers().stream().map(this::mapTierToResponse).collect(Collectors.toList())
                : Collections.emptyList();

        Product prod = policy.getProduct();
        Category cat = policy.getCategory();

        return VolumeDiscountPolicyResponse.builder()
                .id(policy.getId())
                .code(policy.getCode())
                .name(policy.getName())
                .description(policy.getDescription())
                .targetType(policy.getTargetType())
                .productId(prod != null ? prod.getId() : null)
                .productCode(prod != null ? prod.getCode() : null)
                .productName(prod != null ? prod.getName() : null)
                .productUnit(prod != null ? prod.getUnit() : null)
                .categoryId(cat != null ? cat.getId() : null)
                .categoryCode(cat != null ? cat.getCode() : null)
                .categoryName(cat != null ? cat.getName() : null)
                .customerGroup(policy.getCustomerGroup())
                .customerGroupName(policy.getCustomerGroup() != null ? policy.getCustomerGroup().getDisplayName() : "Tất cả")
                .startDate(policy.getStartDate())
                .endDate(policy.getEndDate())
                .active(policy.getActive())
                .createdBy(policy.getCreatedBy())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .tiers(tierResponses)
                .build();
    }

    private VolumeDiscountTierResponse mapTierToResponse(VolumeDiscountTier tier) {
        String desc;
        if (tier.getMaxQuantity() != null) {
            desc = String.format("Từ %s đến %s: Giảm %s %s",
                    tier.getMinQuantity().stripTrailingZeros().toPlainString(),
                    tier.getMaxQuantity().stripTrailingZeros().toPlainString(),
                    tier.getDiscountValue().stripTrailingZeros().toPlainString(),
                    tier.getDiscountType() == DiscountType.PERCENTAGE ? "%" : "đ/đơn vị");
        } else {
            desc = String.format("Từ %s trở lên: Giảm %s %s",
                    tier.getMinQuantity().stripTrailingZeros().toPlainString(),
                    tier.getDiscountValue().stripTrailingZeros().toPlainString(),
                    tier.getDiscountType() == DiscountType.PERCENTAGE ? "%" : "đ/đơn vị");
        }

        return VolumeDiscountTierResponse.builder()
                .id(tier.getId())
                .minQuantity(tier.getMinQuantity())
                .maxQuantity(tier.getMaxQuantity())
                .discountType(tier.getDiscountType())
                .discountValue(tier.getDiscountValue())
                .tierOrder(tier.getTierOrder())
                .description(desc)
                .build();
    }
}

