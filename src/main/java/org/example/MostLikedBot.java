package org.example;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.v1.Query;
import twitter4j.v1.Status;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MostLikedBot {
    private static final int MAX_TWEET_COUNT = 15;
    private static final int INITIAL_MIN_FAVE = 100000;
    private static final int RETWEET_TIME_HOUR = 23;
    private static final int RETWEET_TIME_MINUTE = 59;
    private static final int RETWEET_TIME_SECOND = 30;

    private final Twitter twitter;

    public MostLikedBot() {
        twitter = Twitter.newBuilder()
                .oAuthAccessToken(Secrets.ACCESS_TOKEN, Secrets.ACCESS_TOKEN_SECRET)
                .oAuthConsumer(Secrets.API_KEY, Secrets.API_KEY_SECRET)
                .build();
    }

    /**
     * Runs the bot indefinitely, finding and retweeting the most liked tweet every day.
     * <p>
     * Retweets happen every day at a configured time {@link #RETWEET_TIME_HOUR} RETWEET_TIME_HOUR,
     * {@link #RETWEET_TIME_MINUTE} RETWEET_TIME_MINUTE and {@link #RETWEET_TIME_SECOND} RETWEET_TIME_SECOND.
     *
     * @throws InterruptedException happens when sleeping (waiting) is interrupted (in this case, by user only)
     */
    public void run() throws InterruptedException {
        while (true) {
            // Compute how much time (in seconds) we have to wait for the next retweet
            ZonedDateTime zdtNow = ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
            ZonedDateTime retweetAt = ZonedDateTime.of(zdtNow.getYear(), zdtNow.getMonthValue(), zdtNow.getDayOfMonth(),
                    RETWEET_TIME_HOUR, RETWEET_TIME_MINUTE, RETWEET_TIME_SECOND, 0, ZoneOffset.UTC);
            long delay = ChronoUnit.SECONDS.between(zdtNow, retweetAt);
            delay = delay < 0 ? 86400 + delay : delay; // the delay must be a positive number

            // Wait for the retweet
            TimeUnit.SECONDS.sleep(delay);

            // Retweet now and continue with next delay
            long tweetId = determineMostLikedTweet();
            retweetTweet(tweetId);
        }
    }

    /**
     * Searches for tweets with filtering
     * <p>
     * Given the number of minimum likes and the day of a month, returns a list of tweets that
     * have at least the specified number of likes and have been created at the specified day of the month
     * or later (but not sooner).
     *
     * @param minFaves the minimum number of likes allowed for a tweet
     * @param since    the day of a month
     * @return         a sample of tweets that match the specified requirements
     */
    private List<Status> searchTweets(int minFaves, int since) {
        Query query = Query.of(String.format("min_faves:%s lang:en since:2022-10-%s", minFaves, since));
        try {
            return twitter.v1().search().search(query).getTweets();
        } catch (TwitterException ignored) {
            System.out.println("Query for tweet search has failed. It might have an incorrect form.");
            return new ArrayList<>();
        }
    }

    /**
     * Searches for and determines the current most liked tweet of the day.
     * <p>
     * Given an initial value of minimum allowed likes for a tweet {@link #INITIAL_MIN_FAVE} INITIAL_MIN_FAVE
     * this method looks for tweets that have more likes than the given value, while iteratively increasing the value
     * until only one tweet is left (that tweet is the current most liked tweet)
     *
     * @return         id of the most liked tweet
     */
    private long determineMostLikedTweet() {
        int minFaves = INITIAL_MIN_FAVE;
        int todayIs = ZonedDateTime.now(ZoneOffset.UTC).getDayOfMonth();

        List<Status> tweets = searchTweets(minFaves, todayIs);

        // Case when the initial minimum likes is set too big -> we must reduce it until we start getting
        // at least one tweet match
        while (tweets.size() < 1) {
            minFaves /= 2;
            tweets = searchTweets(minFaves, todayIs);
        }

        // Tracking the maximum number of likes encountered in a tweet
        int maxLikes = tweets.get(0).getFavoriteCount();
        long maxId = tweets.get(0).getId();

        // We are guaranteed to be getting tweets now, we will start increasing the minimum likes value until
        // we are left with only one tweet -> it is guaranteed to have the largest number of likes
        do {
            for (Status tweet : tweets) {
                if (tweet.getFavoriteCount() > maxLikes) {
                    maxLikes = tweet.getFavoriteCount();
                    maxId = tweet.getId();
                }
            }
            tweets = searchTweets(maxLikes, todayIs);
        } while (tweets.size() == MAX_TWEET_COUNT);
        return maxId;
    }

    /**
     * Retweets and likes a specified tweet
     * <p>
     * Retweets and likes a specified tweet while providing additional feedback on the conclusion of
     * the request (whether it succeeded or not)
     *
     * @param tweetId id of the tweet
     */
    private void retweetTweet(long tweetId) {
        try {
            twitter.v1().tweets().retweetStatus(tweetId);     // retweet the tweet
            twitter.v1().favorites().createFavorite(tweetId); // we also give it a like
            System.out.println("Retweet SUCCESSFUL!");
            printTweet(tweetId);
        } catch (TwitterException ignored) {
            System.out.println("Retweet FAILED!");
            System.out.println("Tweet has already been retweeted or not found! Doing nothing...");
        }
    }

    /**
     * Provides information about any specified tweet
     * <p>
     * Gives information about the tweet's id, creation date, user who posted, like and retweet counts.
     *
     * @param tweetId id of the tweet
     */
    private void printTweet(long tweetId) {
        try {
            Status tweet = twitter.v1().tweets().lookup(tweetId).get(0);
            System.out.println("Tweet id: " + tweet.getId());
            System.out.println("Created at: " + tweet.getCreatedAt().toString());
            System.out.println("User: @" + tweet.getUser().getScreenName() + " - " + tweet.getText());
            System.out.println("Likes: " + tweet.getFavoriteCount());
            System.out.println("Retweets: " + tweet.getRetweetCount());
            System.out.println();

        } catch (TwitterException ignored) {
            System.out.println("Tweet not found!");
        }
    }
}