package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.service.UploadUserService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UploadUsersMutation {
  private final UploadUserService uploadUserService;

  @Secured(ADMIN)
  @MutationMapping
  public boolean uploadUsers(
      @Argument("file") @Valid @NotNull(message = "File is required") MultipartFile file) {
    log.info("Uploading users from file: {}", file.getOriginalFilename());
    try {
      boolean result = uploadUserService.uploadUsers(file.getInputStream());
      log.info("Successfully uploaded users from file: {}", file.getOriginalFilename());
      return result;
    } catch (IOException e) {
      log.error("Error accessing file stream for: {} - Error: {}",
          file.getOriginalFilename(), e.getMessage(), e);
      throw new RuntimeException("Cannot access file stream", e);
    } catch (Exception e) {
      log.error("Error uploading users from file: {} - Error: {}",
          file.getOriginalFilename(), e.getMessage(), e);
      throw e;
    }
  }
}