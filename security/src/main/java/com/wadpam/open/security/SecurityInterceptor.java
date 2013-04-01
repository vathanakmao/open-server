package com.wadpam.open.security;

import com.wadpam.open.exceptions.AuthenticationFailedException;
import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import org.apache.commons.codec.binary.Base64;

/**
 * A security interceptor responsible for performing authentication.
 * Default authentication mechanism is {@link SecurityInterceptor#AUTH_TYPE_BASIC}.
 * @author sosandstrom
 * @since 16
 */
public class SecurityInterceptor extends HandlerInterceptorAdapter {
    protected static final Logger LOG = LoggerFactory.getLogger(SecurityInterceptor.class);

    protected static final int ERR_SECURITY_BASE = 77000;
    protected static final int ERR_CREDENTIALS_NOT_FOUND = ERR_SECURITY_BASE+1;
    protected static final int ERR_AUTHENTICATION_FAILED = ERR_SECURITY_BASE+2;
    
    public static final String AUTH_TYPE_BASIC = "Basic ";
    public static final String AUTH_TYPE_COOKIE = "Cookie ";
    public static final String AUTH_TYPE_OAUTH = "OAuth ";

    public static final String AUTH_PARAM_BASIC = "jBasic";
    public static final String AUTH_PARAM_COOKIE = "jCookie";
    public static final String AUTH_PARAM_OAUTH = "access_token";

    /** must be same as MardaoPrincipalInterceptor value */
    public static final String ATTR_NAME_USERNAME = "com.wadpam.open.security.username";
    public static final String ATTR_NAME_PRINCIPAL = "com.wadpam.open.security.principal";
    public static final String ATTR_NAME_ROLES = "com.wadpam.open.security.roles";
    public static final String USERNAME_ANONYMOUS = "[ANONYMOUS]";
    
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String PATH_AH = "/_ah/";
    
    public static final TreeSet<String> ROLES_ANONYMOUS = new TreeSet<String>();
    
    static {
        ROLES_ANONYMOUS.add(SecurityDetailsService.ROLE_ANONYMOUS);
    }
    
    
    private String authenticationMechanism = AUTH_TYPE_BASIC;
    private String realmName = "open-server SecurityInterceptor";
    private SecurityDetailsService securityDetailsService;
    
    // Paths
    private final ArrayList<Entry<Pattern, Set<String>>> WHITELISTED_METHODS = 
            new ArrayList<Entry<Pattern, Set<String>>>();

    /**
     * Returns the realm username if the client is authenticated.
     * @param request
     * @param response
     * @param uri
     * @param authValue
     * @param clientUsername
     * @param details
     * @return the realm username if the client is authenticated
     */
    protected String doAuthenticate(HttpServletRequest request, 
            HttpServletResponse response, 
            String uri, 
            String authValue, 
            String clientUsername, 
            Object details) {
        final String clientPass = getClientPassword(request, response, uri, authValue);
        final String realmPass = getRealmPassword(details);
        final boolean matches = realmPass.equals(clientPass);
        return matches ? getRealmUsername(clientUsername, details) : null;
    }

    /**
     * Override to specify authentication value parameter name.
     * This implementation supports {@link SecurityInterceptor#AUTH_TYPE_BASIC}, {@link SecurityInterceptor#AUTH_TYPE_COOKIE}, {@link SecurityInterceptor#AUTH_TYPE_OAUTH}
     * @return authentication value parameter name.
     */
    protected String getAuthenticationParamName() {
        if (AUTH_TYPE_BASIC.equals(authenticationMechanism)) {
            return AUTH_PARAM_BASIC;
        }
        if (AUTH_TYPE_COOKIE.equals(authenticationMechanism)) {
            return AUTH_PARAM_COOKIE;
        }
        if (AUTH_TYPE_OAUTH.equals(authenticationMechanism)) {
            return AUTH_PARAM_OAUTH;
        }
        return null;
    }

    protected String getAuthenticationValue(HttpServletRequest request, HttpServletResponse response, String uri) {
        String value = null;
        
        // param has highest priority:
        final String paramName = getAuthenticationParamName();
        value = request.getParameter(paramName);
        
        if (null == value) {
            
            // consider header next:
            String authorization = request.getHeader(HEADER_AUTHORIZATION);
            if (null != authorization && authorization.startsWith(authenticationMechanism)) {
                
                // strip auth mechanism from header value
                value = authorization.substring(authenticationMechanism.length());
            }
            
            if (null == value) {
                // consider cookie as last resort:
                final Cookie[] cookies = request.getCookies();
                if (null != cookies) {
                    for (Cookie cookie : cookies) {
                        if (paramName.equals(cookie.getName())) {
                            value = cookie.getValue();
                        }
                    }
                }
            }
        }
        
        return value;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException, ServletException {

        final String uri = request.getRequestURI();
        final String method = request.getMethod();

        // get the authentication value:
        String authValue = getAuthenticationValue(request, response, uri);
        LOG.debug("authValue for {} is {}", getAuthenticationParamName(), authValue);
        
        // decode for Basic
        if (null != authValue && AUTH_TYPE_BASIC.equals(authenticationMechanism)) {
            byte buf[] = Base64.decodeBase64(authValue);
            authValue = new String(buf);
        }
        
        final String principalName = isAuthenticated(request, response, handler,
                uri, method, authValue);
        if (null == principalName && null != response && AUTH_TYPE_BASIC.equals(authenticationMechanism)) {
            // No match initiate basic authentication with the client
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", 
                    String.format("Basic realm=\"%s\"", realmName));
        }
        return (null != principalName);
    }
        
    /**
     * Checks if a request is authenticated, based only on uri, method and authValue params.
     * Hence, this method is suitable for unit testing (public for subclass unit tests).
     * @param request not used by this implementation
     * @param response not used by this implementation
     * @param handler not used by this implementation
     * @param uri
     * @param method
     * @param authValue as returned by {@link SecurityInterceptor#getAuthenticationValue(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, java.lang.String) 
     * @return username if authenticated, null otherwise
     */
    public String isAuthenticated(HttpServletRequest request, 
            HttpServletResponse response, 
            Object handler, 
            final String uri, 
            final String method, 
            String authValue) {
        LOG.debug("---- Authenticating using {} for {} {} ----", new Object[] 
            {getAuthenticationMechanism(), method, uri});

        // Skip Environment-specific paths
        final boolean skipPath = skipEnvironmentPaths(request, response, uri);
        LOG.debug("skipPath {}", skipPath);

        // is this request white-listed?
        final boolean whitelisted = isWhitelistedMethod(uri, method);
        LOG.debug("whitelisted {}", whitelisted);
        
        // no credentials supplied?
        if (null == authValue) {
            return populateAnonymousUser(request, skipPath, whitelisted);
        }
        
        // get the username:
        String username = getClientUsername(request, response, uri, authValue);
        LOG.debug("username {}", username);

        // load the user details
        Object details = null;
        try {
            details = securityDetailsService.loadUserDetailsByUsername(request, response, uri, 
                authValue, username);
        }
        catch (AuthenticationFailedException noSuchUser) {
        }
        LOG.debug("details {}", details);
        if (null == details) {
            return populateAnonymousUser(request, skipPath, whitelisted);
        }
        
        // Authenticate:
        String principalName = doAuthenticate(request, response, uri, 
                authValue, username, details);
        LOG.debug("principalName {}", principalName);
        if (null != principalName) {
            if (null != request) {
                request.setAttribute(ATTR_NAME_USERNAME, principalName);
                request.setAttribute(ATTR_NAME_PRINCIPAL, details);
                
                // combine roles
                Collection<String> roles = securityDetailsService.getRolesFromUserDetails(details);
                TreeSet<String> combinedRoles = new TreeSet<String>(roles);
                Collection<String> previousRoles = (Collection<String>) request.getAttribute(ATTR_NAME_ROLES);
                if (null != previousRoles) {
                    combinedRoles.addAll(previousRoles);
                }
                request.setAttribute(ATTR_NAME_ROLES, combinedRoles);
            }
            return principalName;
        }
        return populateAnonymousUser(request, skipPath, whitelisted);
    }


    /**
     * Override to return username from authValue. 
     * For OAuth, this implementation simply returns the authValue.
     * For Basic, it returns the first token from authValue, delimited by ':'
     * @param request
     * @param response
     * @param uri
     * @param authValue header, param or cookie value
     * @return the username to load details for.
     */
    protected String getClientUsername(HttpServletRequest request, HttpServletResponse response, String uri, String authValue) {
        String username = authValue;
        int endIndex = authValue.indexOf(':');
        if (-1 < endIndex && AUTH_TYPE_BASIC.equals(authenticationMechanism)) {
            username = authValue.substring(0, endIndex);            
        }
        return username;
    }
    
    /**
     * Override to return password from authValue. 
     * For OAuth, this implementation simply returns the authValue.
     * For Basic, it returns the second token from authValue, delimited by ':'
     * @param request
     * @param response
     * @param uri
     * @param authValue header, param or cookie value
     * @return the client-provided password
     */
    protected String getClientPassword(HttpServletRequest request, HttpServletResponse response, String uri, String authValue) {
        String password = authValue;
        int beginIndex = authValue.indexOf(':');
        if (-1 < beginIndex && AUTH_TYPE_BASIC.equals(authenticationMechanism)) {
            password = authValue.substring(beginIndex+1);            
        }
        return password;
    }
    
    protected String getRealmPassword(Object details) {
        if (null == details) {
            return null;
        }
        return details.toString();
    }
    
    /**
     * 
     * @param clientUsername
     * @param details
     * @return this implementation returns the specified clientUsername
     */
    protected String getRealmUsername(String clientUsername, Object details) {
        return clientUsername;
    }
    
    protected boolean skipEnvironmentPaths(HttpServletRequest request, HttpServletResponse response, String uri) {
        return null != uri && uri.startsWith(PATH_AH);
    }


    // ---------    Setters and getters ----------------------------------------


    /**
     * @return one of {@link SecurityInterceptor#AUTH_TYPE_BASIC}, 
     *  {@link SecurityInterceptor#AUTH_TYPE_COOKIE}, 
     *  {@link SecurityInterceptor#AUTH_TYPE_OAUTH} or your custom type.
     */
    public String getAuthenticationMechanism() {
        return authenticationMechanism;
    }

    public void setAuthenticationMechanism(String authenticationMechanism) {
        this.authenticationMechanism = authenticationMechanism;
    }
    
    protected boolean isWhitelistedMethod(String requestURI, String method) {
        Matcher matcher;
        for (Entry<Pattern, Set<String>> entry : WHITELISTED_METHODS) {
            matcher = entry.getKey().matcher(requestURI);
            if (matcher.find()) {
                boolean returnValue = entry.getValue().contains(method);
                LOG.debug("{} whitelisted URI {} {}", new Object[] {
                    returnValue, method, entry.getKey()});
                return returnValue;
            }
        }
        return false;
    }
    
    public void setWhitelistedMethods(Collection<Entry<String, Collection<String>>> whitelistedMethods) {
        setListedMethods(whitelistedMethods, WHITELISTED_METHODS);
    }
    
    public static void setListedMethods(Collection<Entry<String, Collection<String>>> methods, 
            List<Entry<Pattern, Set<String>>> listedMethods) {
        listedMethods.clear();
        SimpleImmutableEntry<Pattern, Set<String>> sie;
        for (Entry<String, Collection<String>> entry : methods) {
            sie = new SimpleImmutableEntry<Pattern, Set<String>>(
                    Pattern.compile(entry.getKey()), new TreeSet<String>(entry.getValue()));
            listedMethods.add(sie);
        }
    }

    public void setSecurityDetailsService(SecurityDetailsService securityDetailsService) {
        this.securityDetailsService = securityDetailsService;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    private String populateAnonymousUser(HttpServletRequest request, boolean skipPath, boolean whitelisted) {
        if (skipPath && null != request) {
            // populate request
        }
        
        if (whitelisted && null != request) {
            // populate request
            request.setAttribute(ATTR_NAME_USERNAME, USERNAME_ANONYMOUS);
            request.setAttribute(ATTR_NAME_PRINCIPAL, USERNAME_ANONYMOUS);

            // combine roles
            TreeSet<String> combinedRoles = new TreeSet<String>();
            Collection<String> previousRoles = (Collection<String>) request.getAttribute(ATTR_NAME_ROLES);
            if (null != previousRoles) {
                combinedRoles.addAll(previousRoles);
            }
            combinedRoles.add(SecurityDetailsService.ROLE_ANONYMOUS);
            request.setAttribute(ATTR_NAME_ROLES, combinedRoles);
        }
        return (skipPath || whitelisted) ? USERNAME_ANONYMOUS : null;
    }

}
