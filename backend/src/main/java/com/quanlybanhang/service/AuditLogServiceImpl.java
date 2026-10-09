package com.quanlybanhang.service;

import com.quanlybanhang.model.AuditLog;
import com.quanlybanhang.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void logAction(String entityType, String entityId, String action, String oldValue, String newValue, String username) {
        logAction(entityType, entityId, action, oldValue, newValue, null, username);
    }

    @Override
    @Transactional
    public void logAction(String entityType, String entityId, String action, String oldValue, String newValue, String reason, String username) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .username(username)
                .build();
        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLog> getLogs(String username, String entityType, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(null);
        
        if (username != null && !username.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("username"), username.trim()));
        }
        if (entityType != null && !entityType.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType.trim()));
        }
        if (startDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        }
        if (endDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }
        
        return auditLogRepository.findAll(spec, pageable);
    }
}
