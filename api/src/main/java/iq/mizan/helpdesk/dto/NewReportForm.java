package iq.mizan.helpdesk.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

public record NewReportForm(@NotNull ReportCategory category, @NotBlank String subject, @NotBlank String body, @Nullable String appVersion,
                            @Nullable String platform, @Nullable List<MultipartFile> images) {
}
