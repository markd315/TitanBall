package authserver.matchmaking;


//Copied under MIT License with attribution on 7/23/2019
//https://github.com/jsnider3/Jelo/blob/master/LICENSE

import java.util.List;

public class Rating<E>
{
    private int gamesRecorded;
    public double rating, ratingDelta, previousRating;
    private E id;

    /**
     * Create a player with the given ID.
     */
    public Rating(E id, int gamesRecorded) {
        this.id = id;
        this.rating = 1000;
        this.gamesRecorded = gamesRecorded;
    }

    public Rating(E id, double rating, int gamesRecorded) {
        this.id = id;
        this.rating = rating;
        this.gamesRecorded = gamesRecorded;
    }

    public Rating(List<Rating> ratings, E id, int gamesRecorded) {
        this.rating = 0;
        for(Rating r : ratings){
            this.rating += r.rating;
        }
        this.rating /= ratings.size();
        this.id = id;
        this.gamesRecorded = gamesRecorded;
    }

    /**
     * Record us playing in a match. Change our score to reflect playing
     */
    public void addMatch(Match<E> match) {
        double k = getK();
        ratingDelta += k * (match.getScore(this) - getWinOdds(match.getOpponent(this)));
    }

    public void commitLatestMatch() {
        previousRating = rating;
        rating+=ratingDelta;
        ratingDelta = 0.0;
    }

    /**
     * Get the odds of us winning against the given opponent.
     * @see <a href="https://en.wikipedia.org/wiki/Elo_rating_system#Mathematical_details">Source</a>
     */
    public double getWinOdds(Rating<E> opponent) {
        return 1.0 /(1 + Math.pow(10,
                (opponent.getRating() - getRating()) / 400.0));
    }

    /**
     * Get our ID.
     */
    public E getID() {
        return id;
    }

    /**
     * Get the weight we should assign to new matches.
     */
    public double getK() {
        double k = 25;
        if (gamesRecorded > 15) {
            k = 16;
        }
        return k;
    }

    /**
     * Get our rating.
     */
    public double getRating() {
        return rating;
    }

    /**
     * Set our rating.
     */
    public void setRating(double newrating) {
        rating = newrating;
    }
}