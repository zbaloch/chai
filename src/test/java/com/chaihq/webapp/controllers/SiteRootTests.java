package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SiteRootTests {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class SelfHosted {

        @Autowired
        MockMvc mvc;

        @Test
        void signedOutGoesToSignIn() throws Exception {
            mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
        }

        @Test
        void projectsStillNeedSignIn() throws Exception {
            mvc.perform(get("/projects")).andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Nested
    @SpringBootTest(properties = "chai.landing-page.enabled=true")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class WithLandingPage {

        @Autowired
        MockMvc mvc;

        @Autowired
        UserRepository users;

        @Test
        void signedOutSeesLandingPage() throws Exception {
            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("home"))
                    .andExpect(content().string(containsString("Project management your bank can run on its own servers.")))
                    .andExpect(content().string(containsString("src=\"/images/home/projects.png\"")));
        }

        @Test
        void signedInSkipsLandingPage() throws Exception {
            User user = new User();
            user.setEmail("root-test@example.com");
            user.setFirstName("Root");
            user.setLastName("Test");
            users.save(user);

            MockHttpSession session = new MockHttpSession();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                            user.getEmail(), null, List.of())));

            // No accounts yet, so home is the accounts page
            mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/accounts"));
        }

        @Test
        void landingPageAndItsAssetsArePublic() throws Exception {
            mvc.perform(get("/images/home/projects.png")).andExpect(status().isOk());
            mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
            mvc.perform(get("/site.webmanifest")).andExpect(status().isOk());
            mvc.perform(get("/png/og-image.png")).andExpect(status().isOk());
        }
    }
}
