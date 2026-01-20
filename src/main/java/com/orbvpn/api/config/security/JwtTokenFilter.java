package com.orbvpn.api.config.security;

import static org.springframework.util.StringUtils.hasLength;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.orbvpn.api.service.TokenBlacklistService;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenFilter extends OncePerRequestFilter {

  private final JwtTokenUtil jwtTokenUtil;
  private final UserDetailsService userDetailsService;
  private final TokenBlacklistService tokenBlacklistService;

@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
    FilterChain chain) throws IOException, ServletException {

  String header = request.getHeader("Authorization");
  if (!hasLength(header) || !header.startsWith("Bearer ")) {
    chain.doFilter(request, response);
    return;
  }
  
  // ✅ NEW: Skip OrbMesh API keys - let OrbMeshApiKeyFilter handle them
  final String token = header.split(" ")[1].trim();
  if (token.startsWith("orbmesh_")) {
    chain.doFilter(request, response);
    return;
  }
  
  // Continue with JWT validation
  if (!jwtTokenUtil.validate(token)) {
    chain.doFilter(request, response);
    return;
  }

  // Check if token is revoked/blacklisted
  if (tokenBlacklistService.isTokenRevoked(token)) {
    log.warn("Rejected revoked token for request: {}", request.getRequestURI());
    chain.doFilter(request, response);
    return;
  }

  // Get user identity and set it on the spring security context
  UserDetails userDetails = userDetailsService.loadUserByUsername(jwtTokenUtil.getUsername(token));

  if (userDetails != null) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    if (jwtTokenUtil.isTokenExpiring(token)) {
      response.addHeader("Authorization", jwtTokenUtil.generateAccessToken(userDetails));
    }
  }

  chain.doFilter(request, response);
}

}