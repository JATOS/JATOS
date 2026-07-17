package general.gui;


import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import general.common.Common;

import javax.inject.Singleton;
import java.util.Collections;

/**
 * Configuration of Guice dependency injection for Gui module
 */
public class GuiGuiceModule extends AbstractModule {

    @Provides
    @Singleton
    GoogleIdTokenVerifier provideGoogleIdTokenVerifier() {
        HttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        return new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(Collections.singletonList(Common.getOauthGoogleClientId()))
                .build();
    }
}