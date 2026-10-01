package com.quanlybanhang.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity Category - Quản lý nhóm hàng nhiều cấp (cấu trúc cây phân cấp tối thiểu 3 cấp).
 * S2-06:
 * - Cấp 1: Ngành hàng (Root, parent = null, level = 1)
 * - Cấp 2: Phân nhóm (parent là Cấp 1, level = 2)
 * - Cấp 3+: Nhóm hàng chi tiết / Nhóm con (parent là Cấp 2+, level = 3+)
 */
@Entity
@Table(name = "categories", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    /**
     * Cấp độ trong cây phân cấp:
     * 1 = Ngành hàng gốc (Root)
     * 2 = Phân nhóm cấp 2
     * 3 = Nhóm hàng cấp 3
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer level = 1;

    /**
     * Nhóm hàng cha trong cấu trúc cây.
     * null nếu là ngành hàng gốc cấp 1.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /**
     * Danh sách các nhóm con trực tiếp.
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Category> children = new ArrayList<>();

    /**
     * Danh sách sản phẩm thuộc nhóm này.
     */
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Product> products = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.active == null) {
            this.active = true;
        }
        if (this.level == null) {
            this.level = this.parent != null ? this.parent.getLevel() + 1 : 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
