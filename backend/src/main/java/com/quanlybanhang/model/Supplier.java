package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(name = "contact_person", length = 120)
    private String contactPerson;

    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "supplier")
    @Builder.Default
    private List<WarehouseVoucher> vouchers = new ArrayList<>();
}
