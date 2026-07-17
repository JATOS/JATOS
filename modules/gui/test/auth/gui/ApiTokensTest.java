package auth.gui;

import daos.common.ApiTokenDao;
import http.common.Http.Context;
import json.common.DefaultJson;
import json.common.DomainJsonMapper;
import models.common.ApiToken;
import models.common.User;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.libs.Json;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.ApiTokenService;

import java.util.Arrays;
import java.util.List;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.contentAsString;

/**
 * Unit tests for ApiTokens controller.
 */
public class ApiTokensTest {

    private ApiTokenDao apiTokenDao;
    private ApiTokenService apiTokenService;
    private ApiTokens apiTokens;

    private User user;

    private final DomainJsonMapper domainJsonMapper =
            new DomainJsonMapper(new DefaultJson(), null, null, null, null);

    @Before
    public void setUp() {
        apiTokenDao = mock(ApiTokenDao.class);
        apiTokenService = mock(ApiTokenService.class);
        apiTokens = new ApiTokens(apiTokenDao, apiTokenService, domainJsonMapper);

        user = new User();
        user.setUsername("alice");

        setCurrentContextWithSignedinUser(user);
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    private void setCurrentContextWithSignedinUser(User user) {
        Context context = new Context(Helpers.fakeRequest().build());
        context.args().put(SIGNEDIN_USER, user);
        Context.setCurrent(context);
    }

    @Test
    public void allTokenDataByUser_returnsJsonArrayOfTokens() {
        ApiToken t1 = new ApiToken();
        t1.setId(1L);
        t1.setName("token1");
        t1.setUser(user);
        ApiToken t2 = new ApiToken();
        t2.setId(2L);
        t2.setName("token2");
        t2.setUser(user);
        List<ApiToken> list = Arrays.asList(t1, t2);
        when(apiTokenDao.findByUser(user)).thenReturn(list);

        Result res = apiTokens.allTokenDataByUser();

        assertThat(res.status()).isEqualTo(OK);
        String body = contentAsString(res);
        assertThat(Json.parse(body).get("data").size()).isEqualTo(2);
        assertThat(Json.parse(body).get("data").get(0).get("name").asText()).isEqualTo("token1");
        assertThat(Json.parse(body).get("data").get(1).get("name").asText()).isEqualTo("token2");
        verify(apiTokenDao).findByUser(user);
    }

    @Test
    public void generate_valid_callsService_andReturnsTokenString() {
        ApiToken token = new ApiToken();
        token.setUser(user);
        token.setName("mytoken");
        when(apiTokenService.create(eq(user), eq("mytoken"), isNull())).thenReturn(Pair.of(token, "TOKEN123"));

        Result res = apiTokens.generate("mytoken", 0); // 0 = never expires

        assertThat(res.status()).isEqualTo(OK);
        assertThat(contentAsString(res)).isEqualTo("TOKEN123");
        verify(apiTokenService).create(eq(user), eq("mytoken"), isNull());
    }

    @Test
    public void generate_emptyName_returnsBadRequest() {
        Result res = apiTokens.generate("", 10);
        assertThat(res.status()).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(res)).contains("Name must not be empty");
        verifyNoInteractions(apiTokenService);
    }

    @Test
    public void generate_nameWithHtml_returnsBadRequest() {
        Result res = apiTokens.generate("<b>x</b>", 10);
        assertThat(res.status()).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(res)).contains("No HTML allowed");
        verifyNoInteractions(apiTokenService);
    }

    @Test
    public void generate_negativeExpires_returnsBadRequest() {
        Result res = apiTokens.generate("name", -1);
        assertThat(res.status()).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(res)).contains("Expiration must be >= 0");
        verifyNoInteractions(apiTokenService);
    }

    @Test
    public void remove_notFoundWhenMissing() {
        when(apiTokenDao.find(1L)).thenReturn(null);
        Result res = apiTokens.remove(1L);
        assertThat(res.status()).isEqualTo(NOT_FOUND);
        assertThat(contentAsString(res)).contains("Token doesn't exist");
        verify(apiTokenDao, never()).remove(any());
    }

    @Test
    public void remove_notFoundWhenForeignUser() {
        ApiToken t = new ApiToken();
        User other = new User();
        other.setUsername("bob");
        t.setUser(other);
        when(apiTokenDao.find(2L)).thenReturn(t);

        Result res = apiTokens.remove(2L);

        assertThat(res.status()).isEqualTo(NOT_FOUND);
        verify(apiTokenDao, never()).remove(any());
    }

    @Test
    public void remove_okWhenOwned() {
        ApiToken t = new ApiToken();
        t.setUser(user); // same instance -> identity check passes
        when(apiTokenDao.find(3L)).thenReturn(t);

        Result res = apiTokens.remove(3L);

        assertThat(res.status()).isEqualTo(OK);
        verify(apiTokenDao).remove(t);
    }

    @Test
    public void toggleActive_notFoundWhenMissingOrForeign() {
        when(apiTokenDao.find(4L)).thenReturn(null);
        Result res1 = apiTokens.toggleActive(4L, true);
        assertThat(res1.status()).isEqualTo(NOT_FOUND);

        ApiToken t = new ApiToken();
        User other = new User();
        other.setUsername("charlie");
        t.setUser(other);
        when(apiTokenDao.find(5L)).thenReturn(t);
        Result res2 = apiTokens.toggleActive(5L, false);
        assertThat(res2.status()).isEqualTo(NOT_FOUND);
        verify(apiTokenDao, never()).merge(any());
    }

    @Test
    public void toggleActive_updatesAndOk() {
        ApiToken t = new ApiToken();
        t.setUser(user);
        t.setActive(true);
        when(apiTokenDao.find(6L)).thenReturn(t);

        Result res = apiTokens.toggleActive(6L, false);

        assertThat(res.status()).isEqualTo(OK);
        // token should be updated with new active flag
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        verify(apiTokenDao).merge(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(contentAsString(res)).isEqualTo(" ");
    }

}
