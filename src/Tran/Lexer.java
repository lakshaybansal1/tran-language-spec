package Tran;

/*
Author - Lakshay Bansal.

 */
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Lexer {
    private final TextManager tm;
    private final List<Token> tokens;
    private boolean atStartOfLine;
    private final Stack<Integer> indentStack;


    private static final int INDENT_SIZE = 4;

    public Lexer(String input) {
        tm = new TextManager(input);
        tokens = new ArrayList<>();
        atStartOfLine = true;
        indentStack = new Stack<>();

        indentStack.push(0);
    }

    public List<Token> Lex() throws SyntaxErrorException {
        while (!tm.isAtEnd()) {
            char c = tm.peekCharacter();


            if (atStartOfLine || c == '\n' || c == '\r') {
                if (c == '\n' || c == '\r') {
                    consumeNewline();
                }
                processIndentation();
                atStartOfLine = false;
                continue;
            }

            if (Character.isDigit(c) || (c == '.' && Character.isDigit(tm.peekNextCharacter()))) {
                tokens.add(readNumber());
            } else if (Character.isLetter(c)) {
                tokens.add(readWord());
            } else if (c == '"') {
                tokens.add(readQuotedString());
            } else if (c == '{') {
                readComment();
            } else if (isSpace(c)) {
                tm.getCharacter();
            } else {
                tokens.add(readPunctuation());
            }
        }


        while (indentStack.size() > 1) {
            tokens.add(new Token(Token.TokenTypes.DEDENT, tm.getLine(), tm.getColumn() + 1));
            indentStack.pop();
        }
        return tokens;
    }


    private void consumeNewline() {
        int ln = tm.getLine();
        if (!tm.isAtEnd() && tm.peekCharacter() == '\r') {
            tm.getCharacter();
            if (!tm.isAtEnd() && tm.peekCharacter() == '\n') {
                tm.getCharacter();
            }
        } else {
            tm.getCharacter();
        }
        tokens.add(new Token(Token.TokenTypes.NEWLINE, ln + 1, 0));
        atStartOfLine = true;
    }


    private void processIndentation() throws SyntaxErrorException {
        int count = 0;

        while (!tm.isAtEnd()) {
            char ch = tm.peekCharacter();
            if (ch == ' ') {
                count++;
                tm.getCharacter();
            } else if (ch == '\t') {
                count += INDENT_SIZE;
                tm.getCharacter();
            } else {
                break;
            }
        }

        if (tm.isAtEnd() || tm.peekCharacter() == '\n' || tm.peekCharacter() == '\r') {
            return;
        }

        int indentLevel = count / INDENT_SIZE;
        int currentIndent = indentStack.peek();
        if (indentLevel > currentIndent) {
            // New indent level.
            indentStack.push(indentLevel);
            tokens.add(new Token(Token.TokenTypes.INDENT, tm.getLine(), tm.getColumn() + 1));
        } else if (indentLevel < currentIndent) {
            // Dedent until we reach matching indent level.
            while (indentLevel < currentIndent) {
                tokens.add(new Token(Token.TokenTypes.DEDENT, tm.getLine(), tm.getColumn() + 1));
                indentStack.pop();
                currentIndent = indentStack.peek();
            }

            if (indentLevel != currentIndent) {
                indentStack.push(indentLevel);
                tokens.add(new Token(Token.TokenTypes.INDENT, tm.getLine(), tm.getColumn() + 1));
            }
        }

    }

    private Token readNumber() {
        int startLine = tm.getLine();
        int startCol = tm.getColumn() + 1;
        StringBuilder sb = new StringBuilder();
        boolean seenDot = false;
        if (tm.peekCharacter() == '.') {
            seenDot = true;
            sb.append(tm.getCharacter());
        }
        while (!tm.isAtEnd()) {
            char ch = tm.peekCharacter();
            if (Character.isDigit(ch)) {
                sb.append(tm.getCharacter());
            } else if (ch == '.' && !seenDot) {
                seenDot = true;
                sb.append(tm.getCharacter());
            } else {
                break;
            }
        }
        return new Token(Token.TokenTypes.NUMBER, startLine, startCol, sb.toString());
    }

    private Token readWord() {
        int startLine = tm.getLine();
        int startCol = tm.getColumn() + 1;
        StringBuilder sb = new StringBuilder();
        while (!tm.isAtEnd() &&
                (Character.isLetterOrDigit(tm.peekCharacter()) || tm.peekCharacter() == '_')) {
            sb.append(tm.getCharacter());
        }
        String word = sb.toString();
        Token.TokenTypes type = keywordOrWord(word);
        return new Token(type, startLine, startCol, word);
    }

    private Token.TokenTypes keywordOrWord(String word) {
        switch (word) {
            case "class":       return Token.TokenTypes.CLASS;
            case "interface":   return Token.TokenTypes.INTERFACE;
            case "if":          return Token.TokenTypes.IF;
            case "else":        return Token.TokenTypes.ELSE;
            case "loop":        return Token.TokenTypes.LOOP;
            case "construct":   return Token.TokenTypes.CONSTRUCT;
            case "new":         return Token.TokenTypes.NEW;
            case "private":     return Token.TokenTypes.PRIVATE;
            case "shared":      return Token.TokenTypes.SHARED;
            case "implements":  return Token.TokenTypes.IMPLEMENTS;


            default:            return Token.TokenTypes.WORD;
        }
    }

    private Token readQuotedString() throws SyntaxErrorException {
        int startLine = tm.getLine();
        int startCol = tm.getColumn() + 1;
        tm.getCharacter();  // consume opening quote
        StringBuilder sb = new StringBuilder();
        while (!tm.isAtEnd() && tm.peekCharacter() != '"') {
            sb.append(tm.getCharacter());
        }
        if (tm.isAtEnd()) {
            throw new SyntaxErrorException("Unterminated quoted string", tm.getLine(), tm.getColumn() + 1);
        }
        tm.getCharacter();  // consume closing quote
        return new Token(Token.TokenTypes.QUOTEDSTRING, startLine, startCol, sb.toString());
    }

    private void readComment() throws SyntaxErrorException {
        tm.getCharacter();  // consume '{'
        while (!tm.isAtEnd() && tm.peekCharacter() != '}') {
            tm.getCharacter();
        }
        if (tm.isAtEnd()) {
            throw new SyntaxErrorException("Unterminated comment", tm.getLine(), tm.getColumn() + 1);
        }
        tm.getCharacter();  // consume '}'
    }

    private Token readPunctuation() throws SyntaxErrorException {
        int startLine = tm.getLine();
        int startCol = tm.getColumn() + 1;
        char ch = tm.getCharacter();
        switch (ch) {
            case '(':
                return new Token(Token.TokenTypes.LPAREN, startLine, startCol);
            case ')':
                return new Token(Token.TokenTypes.RPAREN, startLine, startCol);
            case ':':
                return new Token(Token.TokenTypes.COLON, startLine, startCol);
            case '.':
                return new Token(Token.TokenTypes.DOT, startLine, startCol);
            case '+':
                return new Token(Token.TokenTypes.PLUS, startLine, startCol);
            case '-':
                return new Token(Token.TokenTypes.MINUS, startLine, startCol);
            case '*':
                return new Token(Token.TokenTypes.TIMES, startLine, startCol);
            case '/':
                return new Token(Token.TokenTypes.DIVIDE, startLine, startCol);
            case '%':
                return new Token(Token.TokenTypes.MODULO, startLine, startCol);
            case ',':
                return new Token(Token.TokenTypes.COMMA, startLine, startCol);
            case '>':
                if (!tm.isAtEnd() && tm.peekCharacter() == '=') {
                    tm.getCharacter();
                    return new Token(Token.TokenTypes.GREATERTHANEQUAL, startLine, startCol);
                }
                return new Token(Token.TokenTypes.GREATERTHAN, startLine, startCol);
            case '<':
                if (!tm.isAtEnd() && tm.peekCharacter() == '=') {
                    tm.getCharacter();
                    return new Token(Token.TokenTypes.LESSTHANEQUAL, startLine, startCol);
                }
                return new Token(Token.TokenTypes.LESSTHAN, startLine, startCol);
            case '=':
                if (!tm.isAtEnd() && tm.peekCharacter() == '=') {
                    tm.getCharacter();
                    return new Token(Token.TokenTypes.EQUAL, startLine, startCol);
                }
                return new Token(Token.TokenTypes.ASSIGN, startLine, startCol);
            case '!':
                if (!tm.isAtEnd() && tm.peekCharacter() == '=') {
                    tm.getCharacter();
                    return new Token(Token.TokenTypes.NOTEQUAL, startLine, startCol);
                }
                throw new SyntaxErrorException("Unexpected character: " + ch, tm.getLine(), tm.getColumn() + 1);
            default:
                throw new SyntaxErrorException("Unexpected character: " + ch, tm.getLine(), tm.getColumn() + 1);
        }
    }

    private boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\u00A0';
    }
}
