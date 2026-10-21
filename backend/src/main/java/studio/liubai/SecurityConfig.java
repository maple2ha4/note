package studio.liubai;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, Environment env) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/**", "/api/public/**", "/api/health", "/oauth2/**", "/login/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
        .requestCache(c -> c.disable())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                    .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'"
                                + " 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self';"
                                + " connect-src 'self'; frame-ancestors 'none'; base-uri 'self';"
                                + " form-action 'self'")))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204))
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"));
    String issuer = env.getProperty("OIDC_ISSUER_URI"),
        client = env.getProperty("OIDC_CLIENT_ID"),
        secret = env.getProperty("OIDC_CLIENT_SECRET"),
        subject = env.getProperty("OIDC_OWNER_SUBJECT");
    if (issuer != null && client != null && secret != null && subject != null) {
      ClientRegistration registration =
          ClientRegistrations.fromIssuerLocation(issuer)
              .registrationId("owner")
              .clientId(client)
              .clientSecret(secret)
              .scope("openid", "profile")
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .build();
      http.oauth2Login(
          o ->
              o.clientRegistrationRepository(new InMemoryClientRegistrationRepository(registration))
                  .successHandler(
                      (req, res, auth) -> {
                        if (!(auth.getPrincipal() instanceof OidcUser user)
                            || !subject.equals(user.getSubject())) {
                          req.getSession().invalidate();
                          org.springframework.security.core.context.SecurityContextHolder
                              .clearContext();
                          res.sendError(403);
                          return;
                        }
                        res.sendRedirect("/");
                      })
                  .failureHandler((req, res, ex) -> res.sendRedirect("/?auth=failed")));
    }
    return http.build();
  }
}
