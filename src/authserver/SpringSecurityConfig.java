package authserver;

import authserver.jwt.JwtAuthenticationEntryPoint;
import authserver.jwt.JwtAuthenticationFilter;
import authserver.users.identities.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.sql.DataSource;

@Configuration
@Order(SecurityProperties.DEFAULT_FILTER_ORDER)
@EnableGlobalMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true,
        prePostEnabled = true
)
public class SpringSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private DataSource ds;

    @Autowired
    public void configureAMBuilder(AuthenticationManagerBuilder auth) throws Exception {
        auth.jdbcAuthentication().dataSource(ds)
                .authoritiesByUsernameQuery("select email, role FROM users where email=?")
                .usersByUsernameQuery("select email,password, 1 FROM users where email=?")
                .passwordEncoder(passwordEncoder());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .cors()
                .and()
                .csrf()
                    .disable()
                .exceptionHandling()
                .authenticationEntryPoint(unauthorizedHandler)
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/",
                        "/favicon.ico",
                        "/**/*.png",
                        "/**/*.gif",
                        "/**/*.svg",
                        "/**/*.jpg",
                        "/**/*.html",
                        "/**/*.css",
                        "/**/*.js")
                .permitAll()
                .antMatchers("/api/users/entrypoint/**",
                        "/api/users/refresh/**",
                        "/api/users/activate/**", //open for key param
                        "/api/users/renew/**")
                .permitAll()
                .antMatchers("/entrypoint/checkUsernameAvailability", "/entrypoint/checkEmailAvailability")
                .permitAll()
                .anyRequest()
                .authenticated();
        // Add our custom JWT security filter
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

    }

    @Autowired
    CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Override
    public void configure(AuthenticationManagerBuilder authenticationManagerBuilder) throws Exception {
        authenticationManagerBuilder
                .userDetailsService(customUserDetailsService)
                .passwordEncoder(passwordEncoder());
    }

    @Bean(BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

}

/*

    Schema and examples

  	CREATE TABLE users (
	    id int auto_increment primary key,
	    username varchar(255),
	    email varchar(255),
	    password varchar(70),
	    role varchar(10),
	    rating double default 1000.0,
	    wins int default 0,
	    losses int default 0,
	    created timestamp,
	    goals int default 0,
	    points double default 0.0,
        sidegoals int default 0,
        blocks int default 0,
        steals int default 0,
        passes int default 0,
        kills int default 0,
        deaths int default 0,
	    turnovers int default 0,
	    killassists int default 0,
	    goalassists int default 0,
	    rebounds int default 0,
	    rating_1v1 double default 1000.0,
	    wins_1v1 int default 0,
	    losses_1v1 int default 0,
	    goals_1v1 int default 0,
	    points_1v1 double default 0.0,
        sidegoals_1v1 int default 0,
        blocks_1v1 int default 0,
        steals_1v1 int default 0,
        passes_1v1 int default 0,
        kills_1v1 int default 0,
        deaths_1v1 int default 0,
	    turnovers_1v1 int default 0,
	    killassists_1v1 int default 0,
	    goalassists_1v1 int default 0,
	    rebounds_1v1 int default 0,
	    activation varchar(10),
	    subexpiration timestamp,
	    enabled boolean
	);

	CREATE TABLE classes (
	    id int auto_increment primary key,
	    role varchar(32),
	    wins int default 0,
	    losses int default 0,
	    ties int default 0,
	    goals int default 0,
	    points double default 0.0,
        sidegoals int default 0,
        blocks int default 0,
        steals int default 0,
        passes int default 0,
        kills int default 0,
        deaths int default 0,
	    turnovers int default 0,
	    killassists int default 0,
	    goalassists int default 0,
	    rebounds int default 0
	);

	insert into users (id, username, email, password, role, created) values(8,'markd315', 'markd315@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','ADMIN', CURRENT_TIMESTAMP);
	insert into users (id, username, email, password, role, created) values(9,'mattbuster', 'mattbuster@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','USER', CURRENT_TIMESTAMP);
    insert into users (id, username, email, password, role, created) values(1,'u1', 'e1@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','USER', CURRENT_TIMESTAMP);
    insert into users (id, username, email, password, role, created) values(2,'u2', 'e2@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','USER', CURRENT_TIMESTAMP);
    insert into users (id, username, email, password, role, created) values(3,'u3', 'e3@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','USER', CURRENT_TIMESTAMP);
	insert into users (id, username, email, password, role, created) values(4,'u4', 'e4@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','USER', CURRENT_TIMESTAMP);
	insert into users (id, username, email, password, role, created) values(5,'u5', 'e5@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','USER', CURRENT_TIMESTAMP);
	insert into users (id, username, email, password, role, created) values(6,'u6', 'e6@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','USER', CURRENT_TIMESTAMP);
	insert into users (id, username, email, password, role, created) values(7,'u7', 'e7@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.','USER', CURRENT_TIMESTAMP);

     insert into classes (id, role) values(1,'GOALIE');
	 insert into classes (id, role) values(2,'WARRIOR');
	 insert into classes (id, role) values(3,'RANGER');
	 insert into classes (id, role) values(4,'DASHER');
	 insert into classes (id, role) values(5,'MARKSMAN');
	 insert into classes (id, role) values(6,'STEALTH');
	 insert into classes (id, role) values(7,'SUPPORT');
	 insert into classes (id, role) values(8,'ARTISAN');
	 insert into classes (id, role) values(9,'GOLEM');
	 insert into classes (id, role) values(10,'MAGE');
	 insert into classes (id, role) values(11,'BUILDER');
    //'pass' is the test password
 */