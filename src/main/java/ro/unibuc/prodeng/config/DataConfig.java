package ro.unibuc.prodeng.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ro.unibuc.prodeng.model.RoleEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DataConfig implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final String adminPassword;

    public DataConfig(UserRepository userRepository, PasswordEncoder encoder,
                      @Value("${prodeng.adminPassword}") String adminPassword) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsername("admin")) {
            UserEntity adminUser = UserEntity.builder()
                    .username("admin")
                    .name("Admin")
                    .email("admin@email.com")
                    .password(encoder.encode(adminPassword))
                    .roles(new ArrayList<>(List.of(
                            new RoleEntity("ROLE_USER"),
                            new RoleEntity("ROLE_ADMIN")
                    )))
                    .build();
            userRepository.save(adminUser);
            log.info("Default admin user created successfully.");
        } else {
            log.info("Admin user already exists, skipping creation.");
        }
    }
}
