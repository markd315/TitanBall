import authserver.matchmaking.Match;
import authserver.matchmaking.Rating;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RatingsTest {
    @Test
    public void dominantWinIsBetter() {
        Rating<String> wClose = new Rating<>("mark", 0);
        Rating<String> lClose = new Rating<>("matt", 0);
        new Match<>(wClose, lClose, .5);
        Rating<String> wDom = new Rating<>("mark", 0);
        Rating<String> lDom = new Rating<>("matt", 0);
        new Match<>(wDom, lDom, 3.5);
        System.out.println(wDom.getRating());
        System.out.println(wClose.getRating());
        assert wDom.getRating() > wClose.getRating()
                && wClose.getRating() > lClose.getRating()
                && lClose.getRating() > lDom.getRating();
    }

    @Test
    public void defeatingGoodOpponentsIsBetter() {
        Rating<String> wExpected = new Rating<>("mark", 0);
        wExpected.setRating(1200);
        Rating<String> lExpected = new Rating<>("matt", 0);
        lExpected.setRating(800);
        new Match<>(wExpected, lExpected, 4);
        Rating<String> wSurprise = new Rating<>("mark", 0);
        wSurprise.setRating(800);
        Rating<String> lSurprise = new Rating<>("matt", 0);
        lSurprise.setRating(1200);
        new Match<>(wSurprise, lSurprise, 4);
        System.out.println(wExpected.getRating());
        System.out.println(wSurprise.getRating());
        assert wSurprise.getRating() - 800 > wExpected.getRating() - 1200
                && 1200 - lSurprise.getRating() > 800 - lExpected.getRating();
    }

    @Test
    public void diminishingReturnsChanges() {
        Rating<String> w = new Rating<>("mark", 0);
        Rating<String> l = new Rating<>("matt", 0);
        for (int i=0; i<10; i++){
            new Match<>(w, l, 2);
        }
        double wMid = w.getRating();
        double lMid = l.getRating();
        double wDist = w.getRating() - 1000;
        double lDist = 1000 - l.getRating();
        for (int i=0; i<10; i++){
            new Match<>(w, l, 2);
        }
        double wwDist = w.getRating() - wMid;
        double llDist = lMid - l.getRating();
        assert wDist > wwDist;
        assert lDist > llDist;
    }

    @Test
    public void homeAwayEqual(){
        Rating<String> wh = new Rating<>("ha", 0);
        Rating<String> la = new Rating<>("aa", 0);
        new Match<>(wh, la, 3.5);

        Rating<String> lh = new Rating<>("hb", 0);
        Rating<String> wa = new Rating<>("ab", 0);
        new Match<>(lh, wa, -3.5);
        System.out.println(Math.abs(wh.getRating()-wa.getRating()));
        System.out.println(Math.abs(lh.getRating()-la.getRating()));
        assert Math.abs(wh.getRating()-wa.getRating()) < 0.01;
        assert Math.abs(lh.getRating()-la.getRating()) < 0.01;
    }

    @Test
    public void moreDeltaForFewerGamesPlayed(){
        Rating<String> wMany = new Rating<>("ha", 100);
        Rating<String> lMany = new Rating<>("aa", 100);
        new Match<>(wMany, lMany, 3.5);

        Rating<String> wFew = new Rating<>("hb", 0);
        Rating<String> lFew = new Rating<>("ab", 0);
        new Match<>(wFew, lFew, 3.5);
        System.out.println(Math.abs(wMany.getRating()-lFew.getRating()));
        System.out.println(Math.abs(wFew.getRating()-lMany.getRating()));
        assert wFew.getRating()>wMany.getRating();
        assert lMany.getRating()>lFew.getRating();
    }

    @Test
    public void averageTeamTest() {
        List<Rating> home = new ArrayList<>();
        List<Rating> away = new ArrayList<>();
        Rating<String> h1 = new Rating<>("h1", 0);
        Rating<String> h2 = new Rating<>("h2", 0);
        Rating<String> h3 = new Rating<>("h3", 0);
        Rating<String> a1 = new Rating<>("a1", 0);
        Rating<String> a2 = new Rating<>("a2", 0);
        Rating<String> a3 = new Rating<>("a3", 0);
        home.add(h1);
        home.add(h2);
        home.add(h3);
        away.add(a1);
        away.add(a2);
        away.add(a3);
        h1.setRating(1200);
        double originalH1 = h1.rating;
        double originalH2 = h2.rating;
        Rating<String> h = new Rating<>(home, "home", 0);
        Rating<String> a = new Rating<>(away, "away", 0);
        new Match<>(h, a, 2.5).injectAverage(home, away);
        assert (h1.getRating() - originalH1) == (h2.getRating() - originalH2);
        assert (h2.getRating() > a2.getRating());
        new Match<>(h, a, 2.5).injectAverage(home, away);
        assert (h1.getRating() - originalH1) == (h2.getRating() - originalH2);
        assert (h2.getRating() > a2.getRating());
    }
}
