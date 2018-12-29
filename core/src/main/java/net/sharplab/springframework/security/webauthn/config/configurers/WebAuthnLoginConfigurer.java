/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sharplab.springframework.security.webauthn.config.configurers;

import com.webauthn4j.registry.Registry;
import com.webauthn4j.response.attestation.statement.COSEAlgorithmIdentifier;
import net.sharplab.springframework.security.webauthn.WebAuthnProcessingFilter;
import net.sharplab.springframework.security.webauthn.challenge.ChallengeRepository;
import net.sharplab.springframework.security.webauthn.challenge.HttpSessionChallengeRepository;
import net.sharplab.springframework.security.webauthn.options.*;
import net.sharplab.springframework.security.webauthn.server.ServerPropertyProvider;
import net.sharplab.springframework.security.webauthn.userdetails.WebAuthnUserDetailsService;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.MFATokenEvaluator;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.web.authentication.ForwardAuthenticationFailureHandler;
import org.springframework.security.web.authentication.ForwardAuthenticationSuccessHandler;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

import static net.sharplab.springframework.security.webauthn.WebAuthnProcessingFilter.*;

/**
 * Adds webAuthnLogin authentication. All attributes have reasonable defaults making all
 * parameters are optional. If no {@link #loginPage(String)} is specified, a default login
 * page will be generated by the framework.
 *
 * <h2>Security Filters</h2>
 * <p>
 * The following Filters are populated
 *
 * <ul>
 * <li>{@link WebAuthnProcessingFilter}</li>
 * <li>{@link OptionsEndpointFilter}</li>
 * </ul>
 *
 * <h2>Shared Objects Created</h2>
 * <p>
 * The following shared objects are populated
 * <ul>
 * <li>{@link ChallengeRepository}</li>
 * <li>{@link OptionsProvider}</li>
 * <li>{@link ServerPropertyProvider}</li>
 * </ul>
 *
 * <h2>Shared Objects Used</h2>
 * <p>
 * The following shared objects are used:
 *
 * <ul>
 * <li>{@link org.springframework.security.authentication.AuthenticationManager}</li>
 * <li>{@link MFATokenEvaluator}</li>
 * </ul>
 */
public final class WebAuthnLoginConfigurer<H extends HttpSecurityBuilder<H>> extends
        AbstractAuthenticationFilterConfigurer<H, WebAuthnLoginConfigurer<H>, WebAuthnProcessingFilter> {

    //~ Instance fields
    // ================================================================================================
    private ChallengeRepository challengeRepository;
    private OptionsProvider optionsProvider;
    private Registry registry;
    private ServerPropertyProvider serverPropertyProvider;

    private String rpId;
    private String rpName;
    private List<PublicKeyCredentialParameters> publicKeyCredParams = new ArrayList<>();

    private String usernameParameter = SPRING_SECURITY_FORM_USERNAME_KEY;
    private String passwordParameter = SPRING_SECURITY_FORM_PASSWORD_KEY;
    private String credentialIdParameter = SPRING_SECURITY_FORM_CREDENTIAL_ID_KEY;
    private String clientDataParameter = SPRING_SECURITY_FORM_CLIENTDATA_KEY;
    private String authenticatorDataParameter = SPRING_SECURITY_FORM_AUTHENTICATOR_DATA_KEY;
    private String signatureParameter = SPRING_SECURITY_FORM_SIGNATURE_KEY;
    private String clientExtensionsJSONParameter = SPRING_SECURITY_FORM_CLIENT_EXTENSIONS_JSON_KEY;

    private final WebAuthnLoginConfigurer<H>.PublicKeyCredParamsConfig publicKeyCredParamsConfigurer = new WebAuthnLoginConfigurer<H>.PublicKeyCredParamsConfig();

    public WebAuthnLoginConfigurer() {
        super(new WebAuthnProcessingFilter(), null);
    }

    public static WebAuthnLoginConfigurer<HttpSecurity> webAuthnLogin() {
        return new WebAuthnLoginConfigurer<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(H http) throws Exception {
        ApplicationContext applicationContext = http.getSharedObject(ApplicationContext.class);

        if (challengeRepository == null) {
            String[] beanNames = applicationContext.getBeanNamesForType(ChallengeRepository.class);
            if (beanNames.length == 0) {
                challengeRepository = new HttpSessionChallengeRepository();
            } else {
                challengeRepository = applicationContext.getBean(ChallengeRepository.class);
            }
        }
        http.setSharedObject(ChallengeRepository.class, challengeRepository);

        if (optionsProvider == null) {
            WebAuthnUserDetailsService userDetailsService = applicationContext.getBean(WebAuthnUserDetailsService.class);
            optionsProvider = new OptionsProviderImpl(userDetailsService, challengeRepository);
        }
        http.setSharedObject(OptionsProvider.class, optionsProvider);

        if (registry == null){
            String[] beanNames = applicationContext.getBeanNamesForType(Registry.class);
            if (beanNames.length == 0) {
                registry = new Registry();
            } else {
                registry = applicationContext.getBean(Registry.class);
            }
        }

        if (serverPropertyProvider == null) {
            // Since ServerPropertyProvider requires initialization,
            // it is not instantiated manually, but retrieved from applicationContext.
            serverPropertyProvider = applicationContext.getBean(ServerPropertyProvider.class);
        }
        http.setSharedObject(ServerPropertyProvider.class, serverPropertyProvider);

        super.init(http);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configure(H http) throws Exception {
        super.configure(http);

        this.getAuthenticationFilter().setServerPropertyProvider(serverPropertyProvider);

        this.optionsProvider.setRpId(rpId);
        this.optionsProvider.setRpName(rpName);
        this.optionsProvider.setPublicKeyCredParams(this.publicKeyCredParamsConfigurer.parameters);

        this.getAuthenticationFilter().setUsernameParameter(usernameParameter);
        this.optionsProvider.setUsernameParameter(usernameParameter);
        getAuthenticationFilter().setPasswordParameter(passwordParameter);
        this.optionsProvider.setPasswordParameter(passwordParameter);
        this.getAuthenticationFilter().setCredentialIdParameter(credentialIdParameter);
        this.optionsProvider.setCredentialIdParameter(credentialIdParameter);
        this.getAuthenticationFilter().setClientDataParameter(clientDataParameter);
        this.optionsProvider.setClientDataParameter(clientDataParameter);
        this.getAuthenticationFilter().setAuthenticatorDataParameter(authenticatorDataParameter);
        this.optionsProvider.setAuthenticatorDataParameter(authenticatorDataParameter);
        this.getAuthenticationFilter().setSignatureParameter(signatureParameter);
        this.optionsProvider.setSignatureParameter(signatureParameter);
        this.getAuthenticationFilter().setClientExtensionsJSONParameter(clientExtensionsJSONParameter);
        this.optionsProvider.setClientExtensionsJSONParameter(clientExtensionsJSONParameter);

        configureOptionsEndpointFilter(http);
    }

    private void configureOptionsEndpointFilter(H http) {
        OptionsEndpointFilter optionsEndpointFilter = new OptionsEndpointFilter(optionsProvider, registry);

        MFATokenEvaluator mfaTokenEvaluator = http.getSharedObject(MFATokenEvaluator.class);
        if (mfaTokenEvaluator != null) {
            optionsEndpointFilter.setMFATokenEvaluator(mfaTokenEvaluator);
        }

        AuthenticationTrustResolver trustResolver = http.getSharedObject(AuthenticationTrustResolver.class);
        if (trustResolver != null) {
            optionsEndpointFilter.setTrustResolver(trustResolver);
        }

        http.addFilterAfter(optionsEndpointFilter, SessionManagementFilter.class);
    }

    public WebAuthnLoginConfigurer<H> rpId(String rpId) {
        this.rpId = rpId;
        return this;
    }

    public WebAuthnLoginConfigurer<H> rpName(String rpName) {
        this.rpName = rpName;
        return this;
    }

    public WebAuthnLoginConfigurer<H>.PublicKeyCredParamsConfig publicKeyCredParams() {
        return this.publicKeyCredParamsConfigurer;
    }

    /**
     * The HTTP parameter to look for the username when performing authentication. Default
     * is "username".
     *
     * @param usernameParameter the HTTP parameter to look for the username when
     *                          performing authentication
     * @return the {@link FormLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> usernameParameter(String usernameParameter) {
        this.usernameParameter = usernameParameter;
        return this;
    }

    /**
     * The HTTP parameter to look for the password when performing authentication. Default
     * is "password".
     *
     * @param passwordParameter the HTTP parameter to look for the password when
     *                          performing authentication
     * @return the {@link WebAuthnLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> passwordParameter(String passwordParameter) {
        this.passwordParameter = passwordParameter;
        return this;
    }

    /**
     * The HTTP parameter to look for the credentialId when performing authentication. Default
     * is "credentialId".
     *
     * @param credentialIdParameter the HTTP parameter to look for the credentialId when
     *                              performing authentication
     * @return the {@link WebAuthnLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> credentialIdParameter(String credentialIdParameter) {
        this.credentialIdParameter = credentialIdParameter;
        return this;
    }

    /**
     * The HTTP parameter to look for the clientData when performing authentication. Default
     * is "clientData".
     *
     * @param clientDataParameter the HTTP parameter to look for the clientData when
     *                            performing authentication
     * @return the {@link WebAuthnLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> clientDataParameter(String clientDataParameter) {
        this.clientDataParameter = clientDataParameter;
        return this;
    }

    /**
     * The HTTP parameter to look for the authenticatorData when performing authentication. Default
     * is "authenticatorData".
     *
     * @param authenticatorDataParameter the HTTP parameter to look for the authenticatorData when
     *                                   performing authentication
     * @return the {@link WebAuthnLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> authenticatorDataParameter(String authenticatorDataParameter) {
        this.authenticatorDataParameter = authenticatorDataParameter;
        return this;
    }

    /**
     * The HTTP parameter to look for the signature when performing authentication. Default
     * is "signature".
     *
     * @param signatureParameter the HTTP parameter to look for the signature when
     *                           performing authentication
     * @return the {@link WebAuthnLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> signatureParameter(String signatureParameter) {
        this.signatureParameter = signatureParameter;
        return this;
    }

    /**
     * The HTTP parameter to look for the clientExtensionsJSON when performing authentication. Default
     * is "clientExtensionsJSON".
     *
     * @param clientExtensionsJSONParameter the HTTP parameter to look for the clientExtensionsJSON when
     *                                      performing authentication
     * @return the {@link WebAuthnLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> clientExtensionsJSONParameter(String clientExtensionsJSONParameter) {
        this.clientExtensionsJSONParameter = clientExtensionsJSONParameter;
        return this;
    }

    /**
     * Forward Authentication Success Handler
     *
     * @param forwardUrl the target URL in case of success
     * @return he {@link WebAuthnLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> successForwardUrl(String forwardUrl) {
        successHandler(new ForwardAuthenticationSuccessHandler(forwardUrl));
        return this;
    }

    /**
     * Forward Authentication Failure Handler
     *
     * @param forwardUrl the target URL in case of failure
     * @return he {@link WebAuthnLoginConfigurer} for additional customization
     */
    public WebAuthnLoginConfigurer<H> failureForwardUrl(String forwardUrl) {
        failureHandler(new ForwardAuthenticationFailureHandler(forwardUrl));
        return this;
    }

    /**
     * <p>
     * Specifies the URL to send users to if login is required. If used with
     * {@link WebSecurityConfigurerAdapter} a default login page will be generated when
     * this attribute is not specified.
     * </p>
     *
     * @param loginPage login page
     * @return the {@link WebAuthnLoginConfigurer} for additional customization
     */
    @Override
    public WebAuthnLoginConfigurer<H> loginPage(String loginPage) {
        return super.loginPage(loginPage);
    }

    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, "POST");
    }

    public WebAuthnLoginConfigurer<H> challengeRepository(ChallengeRepository challengeRepository) {
        Assert.notNull(challengeRepository, "challengeRepository cannot be null");
        this.challengeRepository = challengeRepository;
        return this;
    }

    public WebAuthnLoginConfigurer<H> optionsProvider(OptionsProvider optionsProvider){
        Assert.notNull(optionsProvider, "optionsProvider cannot be null");
        this.optionsProvider = optionsProvider;
        return this;
    }

    public WebAuthnLoginConfigurer<H> serverPropertyProvider(ServerPropertyProvider serverPropertyProvider) {
        Assert.notNull(serverPropertyProvider, "serverPropertyProvider cannot be null");
        this.serverPropertyProvider = serverPropertyProvider;
        return this;
    }

    public class PublicKeyCredParamsConfig {

        private List<PublicKeyCredentialParameters> parameters = new ArrayList<>();

        public PublicKeyCredParamsConfig addPublicKeyCredParams(PublicKeyCredentialType type, COSEAlgorithmIdentifier alg){
            parameters.add(new PublicKeyCredentialParameters(type, alg));
            return this;
        }

        public WebAuthnLoginConfigurer<H> and() {
            return WebAuthnLoginConfigurer.this;
        }

    }

}
