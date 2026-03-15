package ro.unibuc.prodeng.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.service.AuthenticationServiceImplementation;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthenticationController {
    @Autowired
    private AuthenticationServiceImplementation authenticationService;

    @PostMapping("/signin")
    @Operation(summary = "Authenticate and receive a JWT token")
    public ResponseEntity<String> authenticateUser(@Valid @RequestBody SignInRequest signInRequest) {
        String jwt = authenticationService.signInUser(signInRequest);
        return new ResponseEntity<>(jwt, HttpStatus.OK);
    }

    @PostMapping("/signup")
    @Operation(summary = "Create a new account and receive a JWT token")
    public ResponseEntity<String> createUser(@Valid @RequestBody SignUpRequest signUpRequest) {
        String jwt = authenticationService.signUpUser(signUpRequest);
        return new ResponseEntity<>(jwt, HttpStatus.OK);
    }
}
