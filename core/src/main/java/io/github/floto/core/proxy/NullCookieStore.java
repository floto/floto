package io.github.floto.core.proxy;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;

import java.util.Collections;
import java.util.Date;
import java.util.List;

class NullCookieStore implements CookieStore {
    @Override
    public void addCookie(Cookie cookie) {
        // Do nothing
    }

    @Override
    public List<Cookie> getCookies() {
        return Collections.emptyList();
    }

    @Override
    public boolean clearExpired(Date date) {
        return false;
    }

    @Override
    public void clear() {

    }
}
