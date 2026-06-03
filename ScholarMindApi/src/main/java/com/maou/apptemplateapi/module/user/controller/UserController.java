package com.maou.apptemplateapi.module.user.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.user.dto.PasswordUpdateRequest;
import com.maou.apptemplateapi.module.user.dto.UserProfileUpdateRequest;
import com.maou.apptemplateapi.module.user.dto.UserResponse;
import com.maou.apptemplateapi.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ApiResponse<UserResponse> profile() {
        return ApiResponse.success(userService.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success(userService.updateProfile(request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@Valid @RequestBody PasswordUpdateRequest request) {
        userService.updatePassword(request);
        return ApiResponse.success();
    }
}
