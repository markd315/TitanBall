package authserver.matchmaking;

//Copied and heavily modified under MIT License with attribution on 7/23/2019
//https://github.com/jsnider3/Jelo/blob/master/LICENSE

import java.io.Serializable;
import java.util.List;

public class Match<E>  implements Serializable {
    private final int winnerParamSlot;
    private Rating<E> winner;
    private Rating<E> loser;
    public double winMargin;
    public double fullWinMargin = 2.5;//3.0 extra in case they overscore

    public Match(Rating<E> firstRating, Rating<E> secondRating,
                 double firstParamWinMargin) {
        if(firstParamWinMargin > 0.0){
            winnerParamSlot = 1;
            winner = firstRating;
            loser = secondRating;
        }
        else{
            winnerParamSlot = 2;
            loser = firstRating;
            winner = secondRating;
        }
        this.winMargin = Math.abs(firstParamWinMargin);
        winner.addMatch(this);
        loser.addMatch(this);
        winner.commitLatestMatch();
        loser.commitLatestMatch();
    }

    /**
     * The opponent of the given rating in this match.
     *
     * @param rating played in this match.
     */
    public Rating<E> getOpponent(Rating<E> rating) {
        Rating<E> opponent = null;
        if (rating == winner) {
            opponent = loser;
        } else if (rating == loser) {
            opponent = winner;
        }
        return opponent;
    }

    /**
     * The score of the given rating in this match.
     *
     * @return 1 for a win, 0 for a loss, and 0.5 for a tie.
     * @throws IllegalArgumentException if rating was not in this match.
     */
    public double getScore(Rating<E> rating) {
        double score;
        double teamWonBy;
        //raw is 1 for a win, 0 for a loss
        //adjusted needs to give .6 credit for a barely win, .4 for barely loss
        final double WIN_PRIORITY = .6;
        final double CLOSE_GAME_LEFTOVERS = 1.0 - WIN_PRIORITY;
        if (rating.getID().equals(winner.getID())) {
            score = WIN_PRIORITY;
            double dominationBonus = (winMargin / fullWinMargin);
            score += dominationBonus * (CLOSE_GAME_LEFTOVERS);
        } else if(rating.getID().equals(loser.getID())){
            score = CLOSE_GAME_LEFTOVERS;
            double dominatedPenalty = (winMargin / fullWinMargin);
            score -= dominatedPenalty * CLOSE_GAME_LEFTOVERS;
        }else{
            throw new IllegalArgumentException();
        }
        return score;
    }

    public void injectAverage(List<Rating> first, List<Rating> second) {
        List<Rating> winnerT;
        List<Rating> loserT;
        if(winnerParamSlot == 1){
            winnerT = first;
            loserT = second;
        }else{
            winnerT = second;
            loserT = first;
        }
        double kNeutWinDelta = (winner.rating - winner.previousRating)/winner.getK();
        double kNeutLoseDelta = (loser.rating - loser.previousRating)/loser.getK();
        for(Rating r : winnerT){
            r.setRating(r.getRating() + (kNeutWinDelta*r.getK()));
        }
        for(Rating r : loserT){
            r.setRating(r.getRating() + (kNeutLoseDelta*r.getK()));
        }
    }
}
