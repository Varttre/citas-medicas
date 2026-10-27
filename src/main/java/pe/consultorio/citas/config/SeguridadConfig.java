package pe.consultorio.citas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
Seguridad web: sesión de servidor con formulario de inicio de sesión.
Las rutas de cada perfil se protegen por rol; el aislamiento entre médicos se aplica
además en los servicios y repositorios, no solo aquí.
*/
@Configuration
public class SeguridadConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, ManejadorLoginExitoso exito, ManejadorLoginFallido fallo)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/registro", "/recuperar/**", "/error", "/css/**", "/js/**", "/img/**")
                    .permitAll()
                .requestMatchers("/paciente/**").hasRole("PACIENTE")
                .requestMatchers("/medico/**").hasRole("MEDICO")
                .requestMatchers("/recepcion/**").hasRole("RECEPCION")
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("correo")
                .passwordParameter("contrasena")
                .successHandler(exito)
                .failureHandler(fallo)
                .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }

    /** El superadministrador puede todo lo del administrador, y este todo lo de recepción. */
    @Bean
    static RoleHierarchy jerarquiaRoles() {
        return RoleHierarchyImpl.fromHierarchy("""
            ROLE_SUPERADMIN > ROLE_ADMINISTRADOR
            ROLE_ADMINISTRADOR > ROLE_RECEPCION
            """);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
