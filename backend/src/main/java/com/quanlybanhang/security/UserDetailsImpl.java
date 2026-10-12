package com.quanlybanhang.security;

import com.quanlybanhang.model.User;
import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Permission;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter giữa entity User và Spring Security UserDetails.
 */
@AllArgsConstructor
@Getter
public class UserDetailsImpl implements UserDetails {

    private Long id;
    private String username;
    private String email;
    private String password;
    private String fullName;
    private Boolean active;
    private Long tokenVersion;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserDetailsImpl build(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

        return new UserDetailsImpl(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.getFullName(),
                user.getActive(),
                user.getTokenVersion(),
                authorities
        );
    }

    /**
     * Nạp cả quyền của ma trận vào SecurityContext để các service có thể kiểm
     * tra quyền đã cấu hình thay vì chỉ dựa vào tên vai trò.
     */
    public static UserDetailsImpl build(User user, RolePermissionConfig permissionConfig) {
        List<ERole> roles = user.getRoles().stream().map(role -> role.getName()).toList();
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        permissionConfig.getPermissions(roles).stream()
                .map(Permission::name)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        return new UserDetailsImpl(
                user.getId(), user.getUsername(), user.getEmail(), user.getPassword(),
                user.getFullName(), user.getActive(), user.getTokenVersion(), authorities);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
