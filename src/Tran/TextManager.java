package Tran;

/**
 *
 * Author : Lakshay Bansal
 * This file is used for allow to (managing and navigating) peek at character at specific positions, move through the available text.
 * This file is also used to track the line and column number while through the text.
 *
 *
 */

public class TextManager {
    private final String input;
    private int index;
    private int line;
    private int column;

    public TextManager(String input) {

        if (input != null && input.startsWith("\uFEFF")) {
            input = input.substring(1);
        }
        this.input = input;
        this.index = 0;
        this.line = 1;

        this.column = 1;
    }

    public char peekCharacter() {
        if (isAtEnd()) {
            return '\0';
        }
        return input.charAt(index);
    }


    public char peekNextCharacter() {
        if (index + 1 >= input.length()) {
            return '\0';
        }
        return input.charAt(index + 1);
    }

    public char getCharacter() {
        char ch = peekCharacter();
        index++;
        if (ch == '\r') {

            if (!isAtEnd() && input.charAt(index) == '\n') {
                index++;
            }
            line++;

            column = 1;
        } else if (ch == '\n') {
            line++;
            column = 1;
        } else if (ch == '\t') {
            // Treat a tab as 4 spaces.
            column += 4;
        } else {
            column++;
        }
        return ch;
    }

    public boolean isAtEnd() {
        return index >= input.length();
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
