package com.maou.apptemplateapi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.common.security.JwtTokenService;
import com.maou.apptemplateapi.common.security.LoginType;
import com.maou.apptemplateapi.module.user.dto.*;
import com.maou.apptemplateapi.module.user.entity.UserAccount;
import com.maou.apptemplateapi.module.user.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String role = StringUtils.hasText(request.role()) ? request.role() : UserRole.STUDENT.name();
        if (existsUsername(request.username(), null)) {
            log.warn("user register rejected, scenario=user-register, reason=username-duplicated, username={}", request.username());
            throw new BusinessException(ErrorCode.USER_DUPLICATED);
        }
        UserAccount account = new UserAccount();
        account.setUsername(request.username());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setNickname(request.nickname());
        account.setEmail(request.email());
        account.setRole(role);
        account.setStatus(STATUS_ENABLED);
        userAccountMapper.insert(account);
        log.info("user registered, scenario=user-register, userId={}, username={}, role={}", account.getId(), account.getUsername(), account.getRole());
        return issueToken(account);
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, request.username())
                .eq(UserAccount::getDeleted, 0)
                .last("limit 1"));
        if (account == null) {
            log.warn("user login failed, scenario=user-login, reason=user-not-found, username={}", request.username());
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        if (account.getStatus() == null || account.getStatus() != STATUS_ENABLED) {
            log.warn("user login failed, scenario=user-login, reason=account-disabled, userId={}, username={}", account.getId(), account.getUsername());
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            log.warn("user login failed, scenario=user-login, reason=password-invalid, userId={}, username={}", account.getId(), account.getUsername());
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        log.info("user logged in, scenario=user-login, userId={}, username={}, role={}", account.getId(), account.getUsername(), account.getRole());
        return issueToken(account);
    }

    public UserResponse profile() {
        CurrentUser currentUser = CurrentUserProvider.require();
        return toResponse(requireAccount(currentUser.getId(), "user-profile"));
    }

    @Transactional
    public UserResponse updateProfile(UserProfileUpdateRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        UserAccount account = requireAccount(currentUser.getId(), "user-update-profile");
        if (request.nickname() != null) {
            account.setNickname(request.nickname());
        }
        if (request.email() != null) {
            account.setEmail(request.email());
        }
        if (request.avatar() != null) {
            account.setAvatar(request.avatar());
        }
        userAccountMapper.updateById(account);
        log.info("user profile updated, scenario=user-update-profile, userId={}", account.getId());
        return toResponse(userAccountMapper.selectById(account.getId()));
    }

    @Transactional
    public void updatePassword(PasswordUpdateRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        UserAccount account = requireAccount(currentUser.getId(), "user-update-password");
        if (!passwordEncoder.matches(request.oldPassword(), account.getPasswordHash())) {
            log.warn("user password update rejected, scenario=user-update-password, reason=old-password-invalid, userId={}", account.getId());
            throw new BusinessException(ErrorCode.USER_PASSWORD_INVALID);
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountMapper.updateById(account);
        log.info("user password updated, scenario=user-update-password, userId={}", account.getId());
    }

    public PageResult<UserResponse> listUsers(long page, long size, String keyword, String role, Integer status) {
        requireAdmin("admin-list-users");
        Page<UserAccount> result = userAccountMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getDeleted, 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(UserAccount::getUsername, keyword)
                                .or()
                                .like(UserAccount::getNickname, keyword)
                                .or()
                                .like(UserAccount::getEmail, keyword))
                        .eq(StringUtils.hasText(role), UserAccount::getRole, role)
                        .eq(status != null, UserAccount::getStatus, status)
                        .orderByDesc(UserAccount::getId));
        return PageResult.of(result.getRecords().stream().map(this::toResponse).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatusRequest request) {
        requireAdmin("admin-update-user-status");
        if (request.status() != STATUS_ENABLED && request.status() != STATUS_DISABLED) {
            log.warn("user status update rejected, scenario=admin-update-user-status, userId={}, status={}", id, request.status());
            throw new BusinessException(ErrorCode.USER_STATUS_INVALID);
        }
        UserAccount account = requireAccount(id, "admin-update-user-status");
        account.setStatus(request.status());
        userAccountMapper.updateById(account);
        log.info("user status updated, scenario=admin-update-user-status, userId={}, status={}", id, request.status());
        return toResponse(userAccountMapper.selectById(id));
    }

    private AuthResponse issueToken(UserAccount account) {
        CurrentUser currentUser = CurrentUser.builder()
                .principalId(account.getId())
                .subject(account.getUsername())
                .loginType(UserRole.ADMIN.name().equals(account.getRole()) ? LoginType.ADMIN : LoginType.USER)
                .roleCode(account.getRole())
                .organizationId(null)
                .build();
        return new AuthResponse(jwtTokenService.generateToken(currentUser), jwtTokenService.getExpirationSeconds(), toResponse(account));
    }

    private UserAccount requireAccount(Long id, String scenario) {
        UserAccount account = userAccountMapper.selectById(id);
        if (account == null || account.getDeleted() != 0) {
            log.warn("user not found, scenario={}, userId={}", scenario, id);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return account;
    }

    private CurrentUser requireAdmin(String scenario) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (currentUser.getRole() != UserRole.ADMIN) {
            log.warn("user role denied, scenario={}, userId={}, roleCode={}", scenario, currentUser.getId(), currentUser.getRoleCode());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return currentUser;
    }

    private boolean existsUsername(String username, Long excludeId) {
        return userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)
                .eq(UserAccount::getDeleted, 0)
                .ne(excludeId != null, UserAccount::getId, excludeId)) > 0;
    }

    private UserResponse toResponse(UserAccount account) {
        return new UserResponse(account.getId(), account.getUsername(), account.getNickname(), account.getEmail(),
                account.getAvatar(), account.getRole(), account.getStatus(), account.getCreatedAt(), account.getUpdatedAt());
    }
}
