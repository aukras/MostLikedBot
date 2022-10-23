package org.example;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class App {
    public static void main(String[] args) throws InterruptedException, FileNotFoundException {
        PrintStream out = new PrintStream(
                new FileOutputStream("bot-log.txt", true));
        System.setOut(out);

        MostLikedBot bot = new MostLikedBot();
        bot.run();
    }
}