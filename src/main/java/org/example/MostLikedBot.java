package org.example;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.v1.Query;
import twitter4j.v1.Status;

import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MostLikedBot {
    private static final int MAX_TWEET_COUNT = 15;
    private static final int INITIAL_MIN_FAVE = 100000;
    private static final DateTimeFormatter DATE_FORMATTER_WITH_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd - HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER_NO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String HASHTAGS = "#Trending #Tweet #TrendingNow #Bot #Bots";
    private final Twitter twitter;

    public MostLikedBot() {
        twitter = Twitter.newBuilder()
                .oAuthAccessToken(Secrets.ACCESS_TOKEN, Secrets.ACCESS_TOKEN_SECRET)
                .oAuthConsumer(Secrets.API_KEY, Secrets.API_KEY_SECRET)
                .build();
    }

    /**
     * Finds and quote tweets the most liked tweet of the day
     * <p>
     * The tweet is also liked and information about it is provided afterwards
     */
    public void run()  {
        long tweetId = determineMostLikedTweet();
        quoteTweet(tweetId);
        likeTweet(tweetId);
        followTweetPoster(tweetId);
        printTweet(tweetId);
    }

    /**
     * Searches for tweets with filtering
     * <p>
     * Given the number of minimum likes and a date string (YYYY-MM-DD), returns a sample of tweets that
     * have at least the specified number of likes and have been created at the specified date
     * or later (but not sooner).
     *
     * @param minFaves the minimum number of likes allowed for a tweet
     * @param since    the date string (YYYY-MM-DD)
     * @return a sample of tweets that match the specified requirements
     */
    private List<Status> searchTweets(int minFaves, String since) {
        Query query = Query.of(String.format("min_faves:%d lang:en since:%s", minFaves, since));
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
     * @return id of the most liked tweet
     */
    private long determineMostLikedTweet() {
        System.out.println("Determining the most liked tweet of the day...");
        int minFaves = INITIAL_MIN_FAVE;
        ZonedDateTime zdtNow = ZonedDateTime.now(ZoneOffset.UTC);
        String since = zdtNow.format(DATE_FORMATTER_NO_TIME);
        List<Status> tweets = searchTweets(minFaves, since);

        // Case when the initial minimum likes is set too big -> we must reduce it until we start getting
        // at least one tweet match
        while (tweets.size() < 1) {
            minFaves /= 2;
            tweets = searchTweets(minFaves, since);
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
            tweets = searchTweets(maxLikes, since);
        } while (tweets.size() == MAX_TWEET_COUNT);
        System.out.println("Tweet (" + maxId + ") is the most liked tweet of the day!");
        return maxId;
    }

    /**
     * Follows the poster of the specified tweet
     * <p>
     * Follows the poster of the specified tweet while providing additional feedback on the conclusion of
     * the request (whether it succeeded or not)
     *
     * @param tweetId id of the tweet
     */
    private void followTweetPoster(long tweetId) {
        try {
            Status tweet = twitter.v1().tweets().lookup(tweetId).get(0);
            twitter.v1().friendsFollowers().createFriendship(tweet.getUser().getId());
        } catch (TwitterException ignored) {
            System.out.println("Following the owner of tweet (" + tweetId + ") has failed - ignoring request!");
        }
    }

    /**
     * Likes a specified tweet
     * <p>
     * Likes a specified tweet while providing additional feedback on the conclusion of
     * the request (whether it succeeded or not)
     *
     * @param tweetId id of the tweet
     */
    private void likeTweet(long tweetId) {
        try {
            twitter.v1().favorites().createFavorite(tweetId);
            System.out.println("Tweet (" + tweetId + ") has been liked!");
        } catch (TwitterException ignored) {
            System.out.println("Tweet (" + tweetId + ") has already been liked before - ignoring request!");
        }
    }

    /**
     * Retweets a specified tweet
     * <p>
     * Retweets a specified tweet while providing additional feedback on the conclusion of
     * the request (whether it succeeded or not)
     *
     * @param tweetId id of the tweet
     */
    private void retweetTweet(long tweetId) {
        try {
            twitter.v1().tweets().retweetStatus(tweetId);
            System.out.println("Tweet (" + tweetId + ") has been retweeted!");
        } catch (TwitterException ignored) {
            System.out.println("Tweet (" + tweetId + ") has already been retweeted or not found - ignoring request!");
        }
    }

    /**
     * Quote tweets a specified tweet
     * <p>
     * Quote tweets a specified tweet while providing additional feedback on the conclusion of
     * the request (whether it succeeded or not)
     *
     * @param tweetId id of the tweet
     */
    private void quoteTweet(long tweetId) {
        try {
            Status tweetToBeQuoted = twitter.v1().tweets().lookup(tweetId).get(0);
            String tweetUrl = "https://twitter.com/" + tweetToBeQuoted.getUser().getScreenName() + "/status/" + tweetId;

            String userTag = tweetToBeQuoted.getUser().getScreenName();
            String tweetDate = tweetToBeQuoted.getCreatedAt().atZone(ZoneOffset.UTC).format(DATE_FORMATTER_WITH_TIME);
            String likeCount = NumberFormat.getNumberInstance(Locale.US).format(tweetToBeQuoted.getFavoriteCount());
            String retweetCount = NumberFormat.getNumberInstance(Locale.US).format(tweetToBeQuoted.getRetweetCount());

            String quoteTweet = String.format(
                    "This is the most liked tweet of the day!\n" +
                            "\u2022  Tweeted by: @%s\n" +
                            "\u2022  Tweeted at: %s (UTC)\n" +
                            "\u2022  %s likes \u2764\n" +
                            "\u2022  %s retweets \uD83D\uDD01\n" +
                            "%s\n" +
                            "%s",
                    userTag, tweetDate, likeCount, retweetCount, HASHTAGS, tweetUrl);
            twitter.v1().tweets().updateStatus(quoteTweet);
            System.out.println("Tweet (" + tweetId + ") has been quote tweeted!");
        } catch (TwitterException ignored) {
            System.out.println("Quote tweet for (" + tweetId + ") has failed - ignoring request!");
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
            System.out.println("Retweets: " + tweet.getRetweetCount() + "\n");

        } catch (TwitterException ignored) {
            System.out.println("Printing tweet (" + tweetId + ") has failed - ignoring request!\n");
        }
    }
}