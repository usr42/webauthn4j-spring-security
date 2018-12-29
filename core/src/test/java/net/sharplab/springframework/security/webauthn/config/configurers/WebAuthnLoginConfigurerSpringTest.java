package net.sharplab.springframework.security.webauthn.config.configurers;


import com.webauthn4j.response.client.challenge.DefaultChallenge;
import net.sharplab.springframework.security.webauthn.challenge.ChallengeRepository;
import net.sharplab.springframework.security.webauthn.challenge.HttpSessionChallengeRepository;
import net.sharplab.springframework.security.webauthn.options.OptionsProvider;
import net.sharplab.springframework.security.webauthn.options.OptionsProviderImpl;
import net.sharplab.springframework.security.webauthn.server.ServerPropertyProvider;
import net.sharplab.springframework.security.webauthn.server.ServerPropertyProviderImpl;
import net.sharplab.springframework.security.webauthn.userdetails.WebAuthnUserDetails;
import net.sharplab.springframework.security.webauthn.userdetails.WebAuthnUserDetailsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.Filter;
import java.util.Collections;

import static net.sharplab.springframework.security.webauthn.config.configurers.WebAuthnLoginConfigurer.webAuthnLogin;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
public class WebAuthnLoginConfigurerSpringTest {

    @Autowired
    Filter springSecurityFilterChain;

    private MockMvc mvc;

    @MockBean
    private WebAuthnUserDetailsService userDetailsService;

    @Before
    public void setup() {
        WebAuthnUserDetails mockUserDetails = mock(WebAuthnUserDetails.class);
        when(mockUserDetails.getAuthenticators()).thenReturn(Collections.emptyList());
        when(userDetailsService.loadUserByUsername(any())).thenReturn(mockUserDetails);
    }


    @Test
    public void rootPath_with_anonymous_user_test() throws Exception {
        mvc = MockMvcBuilders.standaloneSetup()
                .addFilter(springSecurityFilterChain)
                .build();

        mvc
                .perform(get("/").with(anonymous()))
                .andExpect(unauthenticated())
                .andExpect(status().is3xxRedirection());
    }

    @Test
    public void optionsEndpointPath_with_anonymous_user_test() throws Exception {
        mvc = MockMvcBuilders.standaloneSetup()
                .addFilter(springSecurityFilterChain)
                .build();

        mvc
                .perform(get("/webauthn/options").with(anonymous()))
                .andExpect(unauthenticated())
                .andExpect(content().json("{\"relyingParty\":{\"id\":\"localhost\",\"name\":null},\"challenge\":\"aFglXMZdQTKD4krvNzJBzA\",\"pubKeyCredParams\":[],\"credentials\":[],\"parameters\":{\"username\":\"username\",\"password\":\"password\",\"credentialId\":\"credentialId\",\"clientData\":\"clientData\",\"authenticatorData\":\"authenticatorData\",\"signature\":\"signature\",\"clientExtensionsJSON\":\"clientExtensionsJSON\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    public void rootPath_with_authenticated_user_test() throws Exception {
        mvc = MockMvcBuilders.standaloneSetup()
                .defaultRequest(get("/").with(user("john")))
                .addFilter(springSecurityFilterChain)
                .build();

        mvc
                .perform(get("/"))
                .andExpect(authenticated())
                .andExpect(status().isNotFound());

    }

    @Test
    public void conditionEndpointPath_with_authenticated_user_test() throws Exception {
        mvc = MockMvcBuilders.standaloneSetup()
                .addFilter(springSecurityFilterChain)
                .build();

        mvc
                .perform(get("/webauthn/options").with(user("john")))
                .andExpect(authenticated())
                .andExpect(status().isOk());
    }

    @EnableWebSecurity
    static class Config extends WebSecurityConfigurerAdapter {

        @Bean
        public ChallengeRepository challengeRepository() {
            ChallengeRepository challengeRepository = mock(ChallengeRepository.class);
            when(challengeRepository.loadOrGenerateChallenge(any())).thenReturn(new DefaultChallenge("aFglXMZdQTKD4krvNzJBzA"));
            return challengeRepository;
        }

        @Bean
        public OptionsProvider optionsProvider(WebAuthnUserDetailsService webAuthnUserDetailsService, ChallengeRepository challengeRepository){
            OptionsProvider optionsProvider = new OptionsProviderImpl(webAuthnUserDetailsService, challengeRepository);
            optionsProvider.setRpId("example.com");
            return optionsProvider;
        }

        @Bean
        public ServerPropertyProvider serverPropertyProvider(OptionsProvider optionsProvider, ChallengeRepository challengeRepository) {
            return new ServerPropertyProviderImpl(optionsProvider, challengeRepository);
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            // Authentication
            http.apply(webAuthnLogin());

            // Authorization
            http.authorizeRequests()
                    .antMatchers("/login").permitAll()
                    .anyRequest().authenticated();
        }

    }
}
