package org.example;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.v1.Query;
import twitter4j.v1.QueryResult;
import twitter4j.v1.Status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MostLikedBot {
    private final Twitter twitter;
    private static final int MAX_TWEET_COUNT = 15;

    public MostLikedBot() {
        twitter = Twitter.newBuilder()
                .oAuthAccessToken(Secrets.ACCESS_TOKEN, Secrets.ACCESS_TOKEN_SECRET)
                .oAuthConsumer(Secrets.API_KEY, Secrets.API_KEY_SECRET)
                .build();
    }

    // runs the bot indefinitely, every night retweeting the most popular tweet for the day seconds before midnight
    public void run() throws TwitterException, InterruptedException {
        while (true) {
            long delay = ChronoUnit.SECONDS.between(LocalTime.now(Clock.systemUTC()), LocalTime.of(23, 59, 30));
            delay = delay < 0 ? 86400 + delay : delay; // need to make sure delay is positive (86400 seconds = 1 day)
            TimeUnit.SECONDS.sleep(delay);

            long tweetId = determineMostPopularTweet();
            retweetTweet(tweetId);

            TimeUnit.MINUTES.sleep(1);
        }
    }

    // Determines the most liked tweet for today
    private long determineMostPopularTweet() throws TwitterException {
        // Initial query that "tries the waters out" with the initial value of minimum likes (minFaves)
        // We must do this to get an understanding of how we should adjust minimum likes value later
        int minFaves = 100000;
        String todayIs = Instant.now().toString().substring(8, 10);

        Query query = Query.of(String.format("min_faves:%s lang:en since:2022-10-%s", minFaves, todayIs));

        QueryResult result = twitter.v1().search().search(query);
        List<Status> tweets = result.getTweets();

        // Cases when the initial minimum likes is set too big -> we must reduce it until we start getting
        // tweet matches
        while (tweets.size() < 1) {
            minFaves /= 2;
            query = Query.of(String.format("min_faves:%s lang:en since:2022-10-%s", minFaves, todayIs));
            result = twitter.v1().search().search(query);
            tweets = result.getTweets();
        }

        int maxLikes = tweets.get(0).getFavoriteCount();
        long maxId = tweets.get(0).getId();

        // Now that we are finally getting tweets, we will start increasing the minimum likes value until
        // we are left with only one tweet -> it is guaranteed to have the largest number of likes
        do {
            for (Status tweet : tweets) {
//                printTweet(twitter, tweet.getId());

                if (tweet.getFavoriteCount() > maxLikes) {
                    maxLikes = tweet.getFavoriteCount();
                    maxId = tweet.getId();
                }
            }

            query = Query.of(String.format("min_faves:%s lang:en since:2022-10-%s", maxLikes, todayIs));
            result = twitter.v1().search().search(query);
            tweets = result.getTweets();
        }
        while (tweets.size() == MAX_TWEET_COUNT);
        return maxId;
    }

    private void retweetTweet(long tweetId) {
        try {
            twitter.v1().tweets().retweetStatus(tweetId);
            twitter.v1().favorites().createFavorite(tweetId);
            System.out.println("Retweet SUCCESSFUL!");
            printTweet(tweetId);
        } catch (TwitterException ignored) {
            // Case when the tweet has already been retweeted
            System.out.println("Retweet FAILED!");
            System.out.println("Tweet has already been retweeted or not found! Doing nothing...");
        }
    }

    private void printTweet(long tweetId) {
        try {
            Status tweet = twitter.v1().tweets().lookup(tweetId).get(0);
            System.out.println("Tweet id: " + tweet.getId());
            System.out.println("Created at: " + tweet.getCreatedAt().toString());
            System.out.println("User: @" + tweet.getUser().getScreenName() + " - " + tweet.getText());
            System.out.println("Likes: " + tweet.getFavoriteCount());
            System.out.println("Retweets: " + tweet.getRetweetCount());
            System.out.println();

        } catch (TwitterException e) {
            System.out.println("Tweet not found!");
        }
    }
}
