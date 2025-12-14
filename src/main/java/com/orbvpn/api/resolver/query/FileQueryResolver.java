package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.FileView;
import com.orbvpn.api.service.FileService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FileQueryResolver {
  private final FileService fileService;

  @Secured(USER)
  @QueryMapping
  public List<FileView> files() {
    log.info("Fetching all files");
    try {
      List<FileView> files = fileService.getFiles();
      log.info("Successfully retrieved {} files", files.size());
      return files;
    } catch (Exception e) {
      log.error("Error fetching files - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public FileView file(@Argument @Valid @Positive(message = "File ID must be positive") Integer id) {
    log.info("Fetching file with id: {}", id);
    try {
      FileView file = fileService.getFile(id);
      if (file == null) {
        throw new NotFoundException("File not found with id: " + id);
      }
      log.info("Successfully retrieved file: {}", id);
      return file;
    } catch (NotFoundException e) {
      log.warn("File not found - ID: {} - Error: {}", id, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error fetching file: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}