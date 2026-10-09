package com.quanlybanhang.service;

import com.quanlybanhang.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface AuditLogService {
    void logAction(String entityType, String entityId, String action, String oldValue, String newValue, String username);
    void logAction(String entityType, String entityId, String action, String oldValue, String newValue, String reason, String username);
    Page<AuditLog> getLogs(String username, String entityType, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
}
