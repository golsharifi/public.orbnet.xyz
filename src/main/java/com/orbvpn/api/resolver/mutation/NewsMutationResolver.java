package com.orbvpn.api.resolver.mutation;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

import com.orbvpn.api.domain.dto.NewsEdit;
import com.orbvpn.api.domain.dto.NewsView;
import com.orbvpn.api.service.NewsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import lombok.extern.slf4j.Slf4j;
import jakarta.validation.constraints.Positive;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NewsMutationResolver {

  private final NewsService newsService;

  @Secured(ADMIN)
  @MutationMapping
  public NewsView createNews(@Argument @Valid NewsEdit news) {
    log.info("Creating new news item");
    try {
      return newsService.createNews(news);
    } catch (Exception e) {
      log.error("Error creating news - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public NewsView editNews(
      @Argument @Valid @Positive(message = "News ID must be positive") int id,
      @Argument @Valid NewsEdit news) {
    log.info("Editing news item: {}", id);
    try {
      return newsService.editNews(id, news);
    } catch (Exception e) {
      log.error("Error editing news: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public NewsView deleteNews(@Argument @Valid @Positive(message = "News ID must be positive") int id) {
    log.info("Deleting news item: {}", id);
    try {
      return newsService.deleteNews(id);
    } catch (Exception e) {
      log.error("Error deleting news: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}