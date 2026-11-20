package iq.mizan.helpdesk.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

public record ReplyForm(@Nullable String body, @Nullable List<MultipartFile> images) {
}
