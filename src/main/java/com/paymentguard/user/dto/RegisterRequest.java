package com.paymentguard.user.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(@NotBlank String name, @Email @NotBlank String email,
                              @Size(min = 8, max = 100) String password) {
}
