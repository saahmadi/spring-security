package org.springframework.security.web.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

public class SecurityContextPersistenceFilterTests {
    Mockery jmock = new JUnit4Mockery();
    TestingAuthenticationToken testToken = new TestingAuthenticationToken("someone", "passwd", "ROLE_A");

    @After
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void contextIsClearedAfterChainProceeds() throws Exception {
        final FilterChain chain = jmock.mock(FilterChain.class);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextPersistenceFilter filter = new SecurityContextPersistenceFilter();
        SecurityContextHolder.getContext().setAuthentication(testToken);
        jmock.checking(new Expectations() {{
            oneOf(chain).doFilter(with(aNonNull(HttpServletRequest.class)), with(aNonNull(HttpServletResponse.class)));
        }});

        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    public void contextIsStillClearedIfExceptionIsThrowByFilterChain() throws Exception {
        final FilterChain chain = jmock.mock(FilterChain.class);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextPersistenceFilter filter = new SecurityContextPersistenceFilter();
        SecurityContextHolder.getContext().setAuthentication(testToken);

        jmock.checking(new Expectations() {{
            oneOf(chain).doFilter(with(aNonNull(HttpServletRequest.class)), with(aNonNull(HttpServletResponse.class)));
            will(throwException(new IOException()));
        }});
        try {
            filter.doFilter(request, response, chain);
            fail();
        } catch(IOException expected) {
        }

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    public void loadedContextContextIsCopiedToSecurityContextHolderAndUpdatedContextIsStored() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextPersistenceFilter filter = new SecurityContextPersistenceFilter();
        final TestingAuthenticationToken beforeAuth = new TestingAuthenticationToken("someoneelse", "passwd", "ROLE_B");
        final SecurityContext scBefore = new SecurityContextImpl();
        final SecurityContext scExpectedAfter = new SecurityContextImpl();
        scExpectedAfter.setAuthentication(testToken);
        scBefore.setAuthentication(beforeAuth);
        final SecurityContextRepository repo = jmock.mock(SecurityContextRepository.class);
        filter.setSecurityContextRepository(repo);

        jmock.checking(new Expectations() {{
            oneOf(repo).loadContext(with(aNonNull(HttpRequestResponseHolder.class))); will(returnValue(scBefore));
            oneOf(repo).saveContext(scExpectedAfter, request, response);
        }});

        final FilterChain chain = new FilterChain() {
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                assertEquals(beforeAuth, SecurityContextHolder.getContext().getAuthentication());
                // Change the context here
                SecurityContextHolder.setContext(scExpectedAfter);
            }
        };

        filter.doFilter(request, response, chain);

        jmock.assertIsSatisfied();
    }

    @Test
    public void filterIsNotAppliedAgainIfFilterAppliedAttributeIsSet() throws Exception {
        final FilterChain chain = jmock.mock(FilterChain.class);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextPersistenceFilter filter = new SecurityContextPersistenceFilter();
        filter.setSecurityContextRepository(jmock.mock(SecurityContextRepository.class));

        jmock.checking(new Expectations() {{
            oneOf(chain).doFilter(request, response);
        }});

        request.setAttribute(SecurityContextPersistenceFilter.FILTER_APPLIED, Boolean.TRUE);
        filter.doFilter(request, response, chain);
        jmock.assertIsSatisfied();
    }

    @Test
    public void sessionIsEagerlyCreatedWhenConfigured() throws Exception {
        final FilterChain chain = jmock.mock(FilterChain.class);
        jmock.checking(new Expectations() {{ ignoring(chain); }});
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextPersistenceFilter filter = new SecurityContextPersistenceFilter();
        filter.setForceEagerSessionCreation(true);
        filter.doFilter(request, response, chain);
        assertNotNull(request.getSession(false));
    }
}
