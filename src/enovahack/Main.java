package enovahack;

import java.io.*;
import java.util.*;
import org.json.*;

public class Main {
    public static void main (String [] args) {
        PokerLib.init();
        PokerAI.init();
        Client client = new Client();
        client.run();
    }
}
