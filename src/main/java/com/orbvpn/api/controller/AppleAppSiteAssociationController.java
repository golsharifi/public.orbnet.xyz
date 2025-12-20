package com.orbvpn.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/.well-known")
@Slf4j
public class AppleAppSiteAssociationController {

    @Autowired
    private ResourceLoader resourceLoader;

    @GetMapping("/apple-app-site-association")
    public void getAppleAppSiteAssociation(HttpServletResponse response) throws IOException {
        Resource resource = resourceLoader
                .getResource("classpath:public/.well-known/apple-app-site-association");
        if (resource.exists()) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setContentLength((int) resource.contentLength());
            InputStream inputStream = resource.getInputStream();
            FileCopyUtils.copy(inputStream, response.getOutputStream());
            log.info("Apple app site association file served");
        } else {
            log.error("Apple app site association file not found");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
