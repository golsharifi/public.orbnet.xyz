package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.NewsView;
import com.orbvpn.api.service.NewsService;
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
public class NewsQueryResolver {
  private final NewsService newsService;

  @Secured(USER)
  @QueryMapping
  public List<NewsView> news() {
    log.info("Fetching all news");
    try {
      List<NewsView> newsList = newsService.getNews();
      log.info("Successfully retrieved {} news items", newsList.size());
      return newsList;
    } catch (Exception e) {
      log.error("Error fetching news - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public NewsView newsById(
      @Argument @Valid @Positive(message = "News ID must be positive") Integer id) {
    log.info("Fetching news with id: {}", id);
    try {
      NewsView news = newsService.getNews(id);
      if (news == null) {
        throw new NotFoundException("News not found with id: " + id);
      }
      log.info("Successfully retrieved news: {}", id);
      return news;
    } catch (NotFoundException e) {
      log.warn("News not found - ID: {} - Error: {}", id, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error fetching news: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}