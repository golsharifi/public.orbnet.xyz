package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.FileEdit;
import com.orbvpn.api.domain.dto.FileView;
import com.orbvpn.api.service.FileService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FileMutationResolver {

  private final FileService fileService;

  @Secured(ADMIN)
  @MutationMapping
  public FileView createFile(@Argument @Valid FileEdit file) {
    log.info("Creating new file");
    try {
      FileView fileView = fileService.createFile(file);
      log.info("Successfully created file: {}", fileView.getId());
      return fileView;
    } catch (Exception e) {
      log.error("Error creating file - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public FileView editFile(
      @Argument @Valid @Positive(message = "File ID must be positive") int id,
      @Argument @Valid FileEdit file) {
    log.info("Editing file: {}", id);
    try {
      return fileService.editFile(id, file);
    } catch (Exception e) {
      log.error("Error editing file: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public FileView deleteFile(@Argument @Valid @Positive(message = "File ID must be positive") int id) {
    log.info("Deleting file: {}", id);
    try {
      return fileService.deleteFile(id);
    } catch (Exception e) {
      log.error("Error deleting file: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}