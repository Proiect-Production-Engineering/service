package ro.unibuc.prodeng.service;

import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;

public interface AuthenticationService {
    String signInUser(SignInRequest signInRequest);
    String signUpUser(SignUpRequest signUpRequest);
}
