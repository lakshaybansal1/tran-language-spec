package Tran;

/*

Author - Lakshay Bansal
Token Manager-  It is used for managing the stream of token.
 */
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class TokenManager {

    private final LinkedList<Token> tokens;


    public TokenManager(List<Token> tokens) {
        this.tokens = new LinkedList<>(tokens);
    }


    public boolean done() {
        return tokens.isEmpty();
    }



    public Optional<Token> matchAndRemove(Token.TokenTypes t) {
        if (done()) {
            return Optional.empty();
        }
        Token first = tokens.getFirst();
        if (first.getType() == t) {
            tokens.removeFirst();
            return Optional.of(first);
        }
        return Optional.empty();
    }


    public Optional<Token> peek(int i) {
        if (i < 0 || i >= tokens.size()) {
            return Optional.empty();
        }
        return Optional.of(tokens.get(i));
    }


    public boolean nextTwoTokensMatch(Token.TokenTypes first, Token.TokenTypes second) {
        if (tokens.size() < 2) {
            return false;
        }
        return tokens.get(0).getType() == first && tokens.get(1).getType() == second;
    }



    public int getCurrentLine() {
        if (done()) {
            return -1;
        }
        return tokens.getFirst().getLineNumber();
    }


    public int getCurrentColumnNumber() {
        if (done()) {
            return -1;
        }
        return tokens.getFirst().getColumnNumber();
    }
}
