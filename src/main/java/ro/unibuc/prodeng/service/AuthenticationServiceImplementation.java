package ro.unibuc.prodeng.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ro.unibuc.prodeng.model.RoleEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.security.jwt.JwtUtilities;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImplementation implements AuthenticationService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtilities jwtUtilities;
    private final PasswordEncoder encoder;
    private final MetricsService metricsService;

    @Override
    public String signInUser(SignInRequest signInRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(signInRequest.username(), signInRequest.password()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return jwtUtilities.generateJwtToken(authentication);
    }

    @Override
    public String signUpUser(SignUpRequest signUpRequest) {
        if ("admin".equals(signUpRequest.username())) {
            throw new IllegalArgumentException("Username 'admin' is reserved.");
        }
        if (userRepository.existsByUsername(signUpRequest.username())) {
            throw new IllegalArgumentException("Username already exists: " + signUpRequest.username());
        }
        if (userRepository.existsByEmail(signUpRequest.email())) {
            throw new IllegalArgumentException("Email already exists: " + signUpRequest.email());
        }

        UserEntity user = UserEntity.builder()
                .username(signUpRequest.username())
                .email(signUpRequest.email())
                .password(encoder.encode(signUpRequest.password()))
                .roles(new ArrayList<>(List.of(new RoleEntity("ROLE_USER"))))
                .build();
        userRepository.save(user);
        metricsService.recordUserCreated();

        log.info("New user registered: {}", user.getUsername());

        SignInRequest signInRequest = new SignInRequest(user.getUsername(), signUpRequest.password());
        return signInUser(signInRequest);
    }
}
