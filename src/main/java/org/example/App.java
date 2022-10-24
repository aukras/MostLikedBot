package org.example;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class App implements HttpFunction {
    private final MostLikedBot bot = new MostLikedBot();

    @Override
    public void service(HttpRequest httpRequest, HttpResponse httpResponse) {
        bot.run();
    }
}