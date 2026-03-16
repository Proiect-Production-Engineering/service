package ro.unibuc.prodeng.service;

import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class AuthenticationServiceImplementation implements AuthenticationService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtilities jwtUtilities;

    @Autowired
    private PasswordEncoder encoder;

    @Override
    public String signInUser(SignInRequest signInRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(signInRequest.getUsername(), signInRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return jwtUtilities.generateJwtToken(authentication);
    }

    @Override
    public String signUpUser(SignUpRequest signUpRequest) {
        if ("admin".equals(signUpRequest.getUsername())) {
            throw new IllegalArgumentException("Username 'admin' is reserved.");
        }
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + signUpRequest.getUsername());
        }
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + signUpRequest.getEmail());
        }

        UserEntity user = UserEntity.builder()
                .username(signUpRequest.getUsername())
                .email(signUpRequest.getEmail())
                .password(encoder.encode(signUpRequest.getPassword()))
                .roles(new ArrayList<>(List.of(new RoleEntity("ROLE_USER"))))
                .build();
        userRepository.save(user);

        SignInRequest signInRequest = SignInRequest.builder()
                .username(user.getUsername())
                .password(signUpRequest.getPassword())
                .build();
        return signInUser(signInRequest);
    }
}
