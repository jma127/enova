package enovahack;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class PokerAI {
    public static final double [] probReplace = {0.4, 0.7, 0.9, 1.0};
    public static final int [][] possReplace = {
    {},
    {0},
    {1},
    {2},
    {3},
    {4},
    {0,1},
    {0,2},
    {0,3},
    {0,4},
    {1,2},
    {1,3},
    {1,4},
    {2,3},
    {2,4},
    {3,4},
    {0,1,2},
    {0,1,3},
    {0,1,4},
    {0,2,3},
    {0,2,4},
    {0,3,4},
    {1,2,3},
    {1,2,4},
    {1,3,4},
    {2,3,4}
    };
    public static int [] deck;
    public static AtomicInteger [] wons;
    public static AtomicInteger [] totals;
    public static void init () {
        deck = new int[52];
        PokerLib.init_deck(deck);
        wons = new AtomicInteger[possReplace.length];
        totals = new AtomicInteger[possReplace.length];
        for (int i = 0; i < possReplace.length; i ++) {
            wons[i] = new AtomicInteger(0);
            totals[i] = new AtomicInteger(0);
        }
    }
    private static double bestprob;
    public static String getBestReplace (State state, long timeMS) {
        int numOther = state.getNonFolded();
        int [] hand = state.getHand();
        long endMS = System.currentTimeMillis() + timeMS;
        ExecutorService threads = Executors.newFixedThreadPool(possReplace.length);
        init();
        int [] best = null;
        bestprob = -1.0;
        for (int i = 0; i < possReplace.length; i ++) {
            MonteCarloThread curThread = new MonteCarloThread(hand, numOther, possReplace[i],
                                                              endMS, wons[i], totals[i]);
            threads.submit(curThread);
        }
        threads.shutdown();
        try {
            threads.awaitTermination(1, TimeUnit.MINUTES);
        } catch (Exception e) {}
        
        for (int i = 0; i < possReplace.length; i ++) {
            double probWin = wons[i].doubleValue() / totals[i].get();
            if (probWin > bestprob) {
                bestprob = probWin;
                best = possReplace[i];
            }
        }
        String replace = "";
        for (int i = 0; i < best.length; i ++)
            replace += PokerLib.cardToString(hand[best[i]]) + "%20";
        System.out.printf("  %.3f %s\n", bestprob, replace);
        return "action_name=replace&cards=" + replace;
    }
    private static String getCallString () {
        return "action_name=call";
    }
    private static String getFoldString () {
        return "action_name=fold";
    }
    private static String getRaiseString (int amount) {
        return "action_name=bet&amount="+amount;
    }
    public static String getBestBet (State state, boolean beforeReplace, long timeMS) { // -1 for fold, 0 for call, other are raises
//        If time to replace, we will try replacing all possible cards, monte carlo, and generate best combo
//        Else, we will probabilistically decide odds of winning and whether to call, raise, or fold
        long endMS = System.currentTimeMillis() + timeMS - 100;
        int [] hand = state.getHand();
        int numOther = state.getNonFolded();
        double prob = 0.0;
        if (beforeReplace) {
            getBestReplace(state, timeMS - 100);
            prob = bestprob;
        }
        else {
            ExecutorService threads = Executors.newFixedThreadPool(possReplace.length);
            for (int i = 0; i < possReplace.length; i ++) {
                MonteCarloThread curThread = new MonteCarloThread(hand, numOther, possReplace[0],
                                                                  endMS, wons[0], totals[0]);
                threads.submit(curThread);
            }
            threads.shutdown();
            try {
                threads.awaitTermination(1, TimeUnit.MINUTES);
            } catch (Exception e) {}
            prob = wons[0].doubleValue() / totals[0].get();
        }
        System.out.printf("  %s replace prob %.3f\n", beforeReplace ? "before" : "after", prob);
        Player [] players = state.players;
        int activePlayers = 0;
        int highWager = 0;
        for (Player p : players) {
            if (!p.isFolded())
                activePlayers ++;
            highWager = Math.max(p.getCurrentBet(), highWager);
        }
        int activeIncludingSelf = activePlayers + 1;
        int wager = (int) ((state.getInitialStack() * (prob * activeIncludingSelf - 1)) / activePlayers * 0.8);
        int maxW = (int) (state.getInitialStack() / 1.8);
        int prevWager = state.getCurrentBet();
        if (wager > maxW)
            wager = maxW;
        System.out.printf("  Wager %d\n", wager);
        if (wager < highWager) {
            if (state.getInitialStack() < highWager)
                highWager = state.getInitialStack() - 1;
            if (prob * Math.log(((double) state.getInitialStack() + ((double) activePlayers * highWager)) / state.getInitialStack()) + (1 - prob) * Math.log((state.getInitialStack() - highWager) / ((double) state.getInitialStack())) >= Math.log(((double) state.getInitialStack() - prevWager)/state.getInitialStack())) {
                System.out.printf("  Calling\n");
                return getCallString();
            }
            else {
                System.out.printf("  Fold\n");
                return getFoldString();
            }
        }
        else {
            System.out.printf("  Raise %d\n", wager - prevWager);
            return getRaiseString(wager - prevWager);
        }
    }
}
