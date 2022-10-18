package org.example;
import twitter4j.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class App {
    public static void main(String[] args) throws TwitterException, InterruptedException, FileNotFoundException {
        PrintStream out = new PrintStream(
                new FileOutputStream("retweet-logs.txt", true));
        System.setOut(out);

        MostLikedBot bot = new MostLikedBot();
        bot.run();
    }
}