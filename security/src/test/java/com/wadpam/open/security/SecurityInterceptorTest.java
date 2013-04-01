/*
 * INSERT COPYRIGHT HERE
 */

package com.wadpam.open.security;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.TreeSet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 *
 * @author sosandstrom
 */
public class SecurityInterceptorTest extends TestCase {
    static final Logger LOG = LoggerFactory.getLogger(SecurityInterceptorTest.class);

    static final String URI = "/api/domain/resource/v10";
    SecurityInterceptor instance;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        instance = new SecurityInterceptor();
        instance.setSecurityDetailsService(new SecurityDetailsService() {
            @Override
            public Object loadUserDetailsByUsername(HttpServletRequest request, 
                    HttpServletResponse response, 
                    String uri, String authValue, String username) {
                if ("domain".equals(username)) {
                    return username;
                }
                throw new com.wadpam.open.exceptions.AuthenticationFailedException(404, username);
            }

            @Override
            public Collection<String> getRolesFromUserDetails(Object details) {
                return Collections.EMPTY_LIST;
            }
        });
        LOG.info("-----           setUp() {}           -----", getName());
    }

    @Override
    protected void tearDown() throws Exception {
        LOG.info("+++++           tearDown() {}        +++++", getName());
        super.tearDown();
    }

    public void testIsAuthenticatedNoCredentials() {
        final String authValue = null;
        String actual = instance.isAuthenticated(null, null, null, 
                URI, "GET", authValue);
        assertNull(actual);
    }
    
    public void testIsWhitelisted() {
        Entry<String, Collection<String>> forUri = new AbstractMap.SimpleImmutableEntry(
                String.format("\\A%s\\z", URI),
                Arrays.asList("GET"));
        Collection<Entry<String, Collection<String>>> whitelistedMethods = Arrays.asList(forUri);
        instance.setWhitelistedMethods(whitelistedMethods);
        final String authValue = null;
        MockHttpServletRequest request = new MockHttpServletRequest();
        String actual = instance.isAuthenticated(request, null, null, 
                URI, "GET", authValue);
        assertEquals("whitelisted", SecurityInterceptor.USERNAME_ANONYMOUS, actual);
        assertEquals("anonymous", SecurityInterceptor.USERNAME_ANONYMOUS, request.getAttribute(SecurityInterceptor.ATTR_NAME_USERNAME));
        assertEquals("anonymous role", SecurityInterceptor.ROLES_ANONYMOUS, request.getAttribute(SecurityInterceptor.ATTR_NAME_ROLES));
    }
    
    public void testIsAuthenticatedNoSuchUser() {
        final String authValue = "test:test";
        String actual = instance.isAuthenticated(null, null, null, 
                URI, "GET", authValue);
        assertNull("Wrong username", actual);
    }
    
    public void testIsAuthenticatedWrongPassword() {
        final String authValue = "domain:test";
        String actual = instance.isAuthenticated(null, null, null, 
                URI, "GET", authValue);
        assertNull("Wrong password", actual);
    }
    
    public void testIsAuthenticated() {
        final String authValue = "domain:domain";
        MockHttpServletRequest request = new MockHttpServletRequest();
        String actual = instance.isAuthenticated(request, null, null, 
                URI, "GET", authValue);
        assertEquals("OK", "domain", actual);
        assertEquals("ATTR", "domain", request.getAttribute(SecurityInterceptor.ATTR_NAME_USERNAME));
    }
    
}
