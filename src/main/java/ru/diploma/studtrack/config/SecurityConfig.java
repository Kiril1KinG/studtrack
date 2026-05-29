package ru.diploma.studtrack.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.diploma.studtrack.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
/**
 * Конфигурирует безопасность приложения и правила доступа к URL.
 */
public class SecurityConfig {

    /**
     * Сервис загрузки данных пользователя для аутентификации.
     */
    private final CustomUserDetailsService userDetailsService;

    @Bean
    /**
     * Создаёт кодировщик паролей BCrypt.
     *
     * @return bean кодировщика паролей
     */
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    /**
     * Создаёт провайдер аутентификации на основе DAO.
     *
     * @return bean провайдера аутентификации
     */
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    /**
     * Возвращает менеджер аутентификации из конфигурации Spring Security.
     *
     * @param config конфигурация аутентификации
     * @return менеджер аутентификации
     * @throws Exception если менеджер не может быть создан
     */
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    /**
     * Формирует цепочку фильтров безопасности.
     *
     * @param http объект настройки HTTP-безопасности
     * @return цепочка фильтров безопасности
     * @throws Exception если при сборке цепочки возникает ошибка
     */
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/projects/**").authenticated()
                .requestMatchers("/tasks/**").authenticated()
                .requestMatchers("/profile/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .defaultSuccessUrl("/projects", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/auth/login?logout")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            )
            .exceptionHandling(exception -> exception
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN))
            );

        return http.build();
    }
}