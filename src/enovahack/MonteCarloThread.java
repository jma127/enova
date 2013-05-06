package enovahack;

import java.util.concurrent.atomic.*;

public class MonteCarloThread implements Runnable {
    private final int [] hand;
    private final int [] repl;
    private final int [] deck;
    private final int numOther;
    private final long stop;
    private final AtomicInteger won;
    private final AtomicInteger total;
    public MonteCarloThread (int [] hand, int numOther, int [] repl,
                             long stop, AtomicInteger won, AtomicInteger total) {
        this.hand = hand.clone();
        this.repl = repl.clone();
        this.deck = new int[52];
        PokerLib.init_deck(this.deck);
        this.numOther = numOther;
        this.stop = stop;
        this.won = won;
        this.total = total;
    }
    public static int [][] getOtherPlayers (int [] curdeck, int numOther) {
        int [][] otherHands = new int[numOther][5];
        int curPos = 0;
        for (int i = 0; i < numOther; i ++) {
            for (int j = 0; j < 5; j ++) {
                while (curdeck[curPos] == -1)
                    curPos ++;
                otherHands[i][j] = curdeck[curPos];
                curdeck[curPos] = -1;
            }
        }
        return otherHands;
    }
    public boolean tryReplace () {
        int [] curhand = hand.clone();
        int [] curdeck = deck.clone();
        PokerLib.shuffle_deck(curdeck);
            //        for (int i = 0; i < 52; i ++)
            //            System.out.println(PokerLib.cardToString(curdeck[i]));
        for (int i = 0; i < 5; i ++) {
            for (int j = 0; j < 52; j ++) {
                if (curdeck[j] == curhand[i]) {
                    curdeck[j] = -1;
                    break;
                }
            }
        }
        int [][] otherHands = getOtherPlayers(curdeck, numOther + 1);
            //        System.out.println(PokerLib.print_hand(hand, 5));
        for (int i : repl)
            curhand[i] = otherHands[0][i];
        otherHands[0] = curhand;
            //        for (int i = 0; i < numOther + 1; i ++)
            //            System.out.println(PokerLib.print_hand(otherHands[i], 5));
        int [] otherVals = new int[numOther + 1];
        for (int i = 0; i < numOther + 1; i ++)
            otherVals[i] = PokerLib.eval_5hand(otherHands[i]);
            //        for (int i = 0; i < numOther + 1; i ++)
            //            System.out.println(otherVals[i]);
        int best = numOther;
        for (int i = 0; i < numOther; i ++) {
            if (otherVals[i] < otherVals[best])
                best = i;
        }
        return best == 0;
    }
    public void run () {
        for (int i = 0;; i ++) {
            if (i % 50 == 0) {
                if (System.currentTimeMillis() >= stop)
                    return;
            }
            total.getAndIncrement();
            if (tryReplace())
                won.getAndIncrement();
        }
    }
}