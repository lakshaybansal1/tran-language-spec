package Tran;
/*
Author - Lakshay Bansal.
This file is used to translate the sequence of tokens into a hierarchical AST.
*/
import AST.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Parser {
    private final TokenManager tokenMgr;
    private final TranNode tranRoot;

    public Parser(TranNode root, List<Token> tokens) {
        this.tokenMgr = new TokenManager(tokens);
        this.tranRoot = root;
    }

    // Tran = ( Class | Interface )*
    public void Tran() throws SyntaxErrorException {
        parseTran();
        while (!tokenMgr.done()) {
            tokenMgr.matchAndRemove(tokenMgr.peek(0).get().getType());
        }
    }

    private void parseTran() throws SyntaxErrorException {
        while (!tokenMgr.done()) {
            Optional<Token> optTok = tokenMgr.peek(0);
            if (optTok.isEmpty()) break;
            Token curTok = optTok.get();
            if (curTok.getType() == Token.TokenTypes.NEWLINE) {
                tokenMgr.matchAndRemove(Token.TokenTypes.NEWLINE);
                continue;
            }
            if (curTok.getType() == Token.TokenTypes.CLASS) {
                ClassNode cls = parseClass();
                tranRoot.Classes.add(cls);
            } else if (curTok.getType() == Token.TokenTypes.INTERFACE) {
                InterfaceNode iface = parseInterface();
                tranRoot.Interfaces.add(iface);
            } else {
                tokenMgr.matchAndRemove(curTok.getType());
            }
        }
    }

    // Interface = "interface" IDENTIFIER NEWLINE INDENT MethodHeader* DEDENT.
    private InterfaceNode parseInterface() throws SyntaxErrorException {
        tokenMgr.matchAndRemove(Token.TokenTypes.INTERFACE)
                .orElseThrow(() -> syntaxError("Expected 'interface'"));
        Token nameToken = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected interface name"));
        String ifaceName = nameToken.getValue();
        requireLineBreak();
        tokenMgr.matchAndRemove(Token.TokenTypes.INDENT)
                .orElseThrow(() -> syntaxError("Expected INDENT after interface declaration"));
        InterfaceNode ifaceNode = new InterfaceNode();
        ifaceNode.name = ifaceName;
        while (!tokenMgr.done()) {
            while (!tokenMgr.done() &&
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
                tokenMgr.matchAndRemove(Token.TokenTypes.NEWLINE);
            }
            if (tokenMgr.peek(0).isEmpty() ||
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.DEDENT) {
                break;
            }
            MethodHeaderNode methodHdr = parseMethodHeader();
            ifaceNode.methods.add(methodHdr);
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.DEDENT)
                .orElseThrow(() -> syntaxError("Expected DEDENT to close interface block"));
        return ifaceNode;
    }

    // Class = "class" IDENTIFIER ( "implements" IDENTIFIER ( "," IDENTIFIER )* )? NEWLINE INDENT.
    //         ( Constructor | MethodDeclaration | Member )* DEDENT.
    private ClassNode parseClass() throws SyntaxErrorException {
        tokenMgr.matchAndRemove(Token.TokenTypes.CLASS)
                .orElseThrow(() -> syntaxError("Expected 'class'"));
        Token nameToken = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected class name"));
        String clsName = nameToken.getValue();
        ClassNode clsNode = new ClassNode();
        clsNode.name = clsName;
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.IMPLEMENTS) {
            tokenMgr.matchAndRemove(Token.TokenTypes.IMPLEMENTS);
            Token ifaceToken = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                    .orElseThrow(() -> syntaxError("Expected interface name after 'implements'"));
            List<String> ifaceNames = new ArrayList<>();
            ifaceNames.add(ifaceToken.getValue());
            while (tokenMgr.peek(0).isPresent() &&
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.COMMA) {
                tokenMgr.matchAndRemove(Token.TokenTypes.COMMA);
                Token nextIface = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                        .orElseThrow(() -> syntaxError("Expected interface name after comma"));
                ifaceNames.add(nextIface.getValue());
            }
            clsNode.interfaces.addAll(ifaceNames);
        }
        requireLineBreak();
        tokenMgr.matchAndRemove(Token.TokenTypes.INDENT)
                .orElseThrow(() -> syntaxError("Expected INDENT after class header"));
        while (!tokenMgr.done()) {
            Token next = tokenMgr.peek(0)
                    .orElseThrow(() -> syntaxError("Unexpected end in class block"));
            if (next.getType() == Token.TokenTypes.DEDENT) break;
            parseMember(clsNode);
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.DEDENT)
                .orElseThrow(() -> syntaxError("Expected DEDENT to close class block"));
        return clsNode;
    }

    // Member = VariableDeclaration (for fields) or Method/Constructor.
    private void parseMember(ClassNode clsNode) throws SyntaxErrorException {
        while (tokenMgr.peek(0).isPresent()) {
            Token next = tokenMgr.peek(0).get();
            if (next.getType() == Token.TokenTypes.SHARED ||
                    next.getType() == Token.TokenTypes.PRIVATE) {
                tokenMgr.matchAndRemove(next.getType());
            } else {
                break;
            }
        }
        Token tok = tokenMgr.peek(0)
                .orElseThrow(() -> syntaxError("Expected member declaration"));
        switch (tok.getType()) {
            case CONSTRUCT -> {
                ConstructorNode cons = parseConstructor();
                clsNode.constructors.add(cons);
            }
            case WORD -> {
                if (tokenMgr.nextTwoTokensMatch(Token.TokenTypes.WORD, Token.TokenTypes.LPAREN)) {
                    MethodDeclarationNode mDecl = parseMethodDeclaration();
                    clsNode.methods.add(mDecl);
                } else {
                    VariableDeclarationNode varDecl = parseVariableDeclaration();
                    MemberNode member = new MemberNode();
                    member.declaration = varDecl;
                    clsNode.members.add(member);
                }
            }
            default -> throw syntaxError("Unexpected token in class member: " + tok.getType());
        }
    }

    // Constructor = "construct" "(" ParameterVariableDeclarations ")" NEWLINE MethodBody.
    private ConstructorNode parseConstructor() throws SyntaxErrorException {
        tokenMgr.matchAndRemove(Token.TokenTypes.CONSTRUCT)
                .orElseThrow(() -> syntaxError("Expected 'construct'"));
        tokenMgr.matchAndRemove(Token.TokenTypes.LPAREN)
                .orElseThrow(() -> syntaxError("Expected '(' after 'construct'"));
        List<VariableDeclarationNode> params = new ArrayList<>();
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
            params = parseParameterVariableDeclarations();
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.RPAREN)
                .orElseThrow(() -> syntaxError("Expected ')' after constructor params"));
        requireLineBreak();
        List<StatementNode> consStmts = parseStatements(); // normal block for constructor
        ConstructorNode consNode = new ConstructorNode();
        consNode.parameters.addAll(params);
        consNode.statements.addAll(consStmts);
        return consNode;
    }

    // MethodDeclaration = "private"? "shared"? MethodHeader NEWLINE MethodBody.
    private MethodDeclarationNode parseMethodDeclaration() throws SyntaxErrorException {
        if (tokenMgr.peek(0).isPresent()) {
            Token next = tokenMgr.peek(0).get();
            if (next.getType() == Token.TokenTypes.PRIVATE ||
                    next.getType() == Token.TokenTypes.SHARED) {
                tokenMgr.matchAndRemove(next.getType());
            }
        }
        MethodHeaderNode header = parseMethodHeader();
        requireLineBreak();
        MethodDeclarationNode mDecl = new MethodDeclarationNode();
        mDecl.name = header.name;
        mDecl.parameters.addAll(header.parameters);
        mDecl.returns.addAll(header.returns);
        parseMethodBody(mDecl);
        return mDecl;
    }

    // MethodHeader = IDENTIFIER "(" ParameterVariableDeclarations ")" (":" ParameterVariableDeclarations)? NEWLINE.
    private MethodHeaderNode parseMethodHeader() throws SyntaxErrorException {
        Token nameTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected method name"));
        String methodName = nameTok.getValue();
        tokenMgr.matchAndRemove(Token.TokenTypes.LPAREN)
                .orElseThrow(() -> syntaxError("Expected '(' after method name"));
        List<VariableDeclarationNode> params = new ArrayList<>();
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
            params = parseParameterVariableDeclarations();
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.RPAREN)
                .orElseThrow(() -> syntaxError("Expected ')' after method parameters"));
        List<VariableDeclarationNode> retDecls = new ArrayList<>();
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.COLON) {
            tokenMgr.matchAndRemove(Token.TokenTypes.COLON);
            retDecls = parseParameterVariableDeclarations();
        }
        requireLineBreak();
        MethodHeaderNode header = new MethodHeaderNode();
        header.name = methodName;
        header.parameters.addAll(params);
        header.returns.addAll(retDecls);
        return header;
    }

    private List<VariableDeclarationNode> parseParameterVariableDeclarations() throws SyntaxErrorException {
        List<VariableDeclarationNode> decls = new ArrayList<>();
        decls.add(parseParameterVariableDeclaration());
        while (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.COMMA) {
            tokenMgr.matchAndRemove(Token.TokenTypes.COMMA);
            decls.add(parseParameterVariableDeclaration());
        }
        return decls;
    }

    // ParameterVariableDeclaration = IDENTIFIER IDENTIFIER.
    private VariableDeclarationNode parseParameterVariableDeclaration() throws SyntaxErrorException {
        Token typeTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected type in parameter declaration"));
        Token varNameTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected variable name in parameter declaration"));
        VariableDeclarationNode decl = new VariableDeclarationNode();
        decl.type = typeTok.getValue();
        decl.name = varNameTok.getValue();
        return decl;
    }

    // VariableDeclaration = IDENTIFIER VariableName ("," VariableName)* NEWLINE.
    private VariableDeclarationNode parseVariableDeclaration() throws SyntaxErrorException {
        Token typeTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected type in variable declaration"));
        Token varNameTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected variable name in variable declaration"));
        VariableDeclarationNode decl = new VariableDeclarationNode();
        decl.type = typeTok.getValue();
        decl.name = varNameTok.getValue();
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.ASSIGN) {
            tokenMgr.matchAndRemove(Token.TokenTypes.ASSIGN);
            ExpressionNode expr = parseExpression();
            decl.initializer = Optional.of(expr);
        }
        requireLineBreak();
        return decl;
    }

    // MethodBody = INDENT ( VariableDeclaration )* Statement* DEDENT.
    private void parseMethodBody(MethodDeclarationNode mDecl) throws SyntaxErrorException {
        tokenMgr.matchAndRemove(Token.TokenTypes.INDENT)
                .orElseThrow(() -> syntaxError("Expected INDENT to start method body"));
        while (!tokenMgr.done()) {
            while (!tokenMgr.done() &&
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
                tokenMgr.matchAndRemove(Token.TokenTypes.NEWLINE);
            }
            if (tokenMgr.done()) break;
            Token next = tokenMgr.peek(0).get();
            if (next.getType() == Token.TokenTypes.DEDENT) break;
            // Heuristic: if next two tokens are WORD then assume a local declaration.
            if (next.getType() == Token.TokenTypes.WORD &&
                    tokenMgr.peek(1).isPresent() &&
                    tokenMgr.peek(1).get().getType() == Token.TokenTypes.WORD) {
                VariableDeclarationNode localDecl = parseVariableDeclaration();
                mDecl.locals.add(localDecl);
            } else {
                StatementNode stmt = parseStatement();
                mDecl.statements.add(stmt);
            }
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.DEDENT)
                .orElseThrow(() -> syntaxError("Expected DEDENT to end method body"));
    }

    // Overloaded parseStatements: if breakOnElse is true, stop when encountering ELSE or DEDENT.
    private List<StatementNode> parseStatements(boolean breakOnElse) throws SyntaxErrorException {
        // Consume leading newlines.
        while (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
            tokenMgr.matchAndRemove(Token.TokenTypes.NEWLINE);
        }
        // Expect an INDENT for a multi-line block.
        tokenMgr.matchAndRemove(Token.TokenTypes.INDENT)
                .orElseThrow(() -> syntaxError("Expected INDENT to start statements block"));
        List<StatementNode> stmts = new ArrayList<>();
        while (!tokenMgr.done()) {
            while (!tokenMgr.done() &&
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
                tokenMgr.matchAndRemove(Token.TokenTypes.NEWLINE);
            }
            if (tokenMgr.peek(0).isEmpty()) break;
            Token next = tokenMgr.peek(0).get();
            if (next.getType() == Token.TokenTypes.DEDENT ||
                    (breakOnElse && next.getType() == Token.TokenTypes.ELSE)) {
                break;
            }
            stmts.add(parseStatement());
        }
        if (!breakOnElse) {
            tokenMgr.matchAndRemove(Token.TokenTypes.DEDENT)
                    .orElseThrow(() -> syntaxError("Expected DEDENT to end statements block"));
        } else {
            if (tokenMgr.peek(0).isPresent() &&
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.DEDENT) {
                tokenMgr.matchAndRemove(Token.TokenTypes.DEDENT);
            }
        }
        return stmts;
    }

    // Normal parseStatements() uses breakOnElse = false.
    private List<StatementNode> parseStatements() throws SyntaxErrorException {
        while (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
            tokenMgr.matchAndRemove(Token.TokenTypes.NEWLINE);
        }
        if (!tokenMgr.peek(0).isPresent() ||
                tokenMgr.peek(0).get().getType() != Token.TokenTypes.INDENT) {
            List<StatementNode> stmts = new ArrayList<>();
            stmts.add(parseStatement());
            while (tokenMgr.peek(0).isPresent() &&
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
                tokenMgr.matchAndRemove(Token.TokenTypes.NEWLINE);
            }
            return stmts;
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.INDENT)
                .orElseThrow(() -> syntaxError("Expected INDENT to start statements block"));
        List<StatementNode> stmts = new ArrayList<>();
        while (!tokenMgr.done()) {
            Token next = tokenMgr.peek(0).orElseThrow(() -> syntaxError("Unexpected end inside statements block"));
            if (next.getType() == Token.TokenTypes.DEDENT) break;
            stmts.add(parseStatement());
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.DEDENT)
                .orElseThrow(() -> syntaxError("Expected DEDENT to end statements block"));
        return stmts;
    }

    // Statement = If | Loop | MethodCall | Assignment.
    private StatementNode parseStatement() throws SyntaxErrorException {
        // Skip any extra INDENT tokens.
        while (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.INDENT) {
            tokenMgr.matchAndRemove(Token.TokenTypes.INDENT);
        }
        Token next = tokenMgr.peek(0)
                .orElseThrow(() -> syntaxError("Expected statement"));
        return switch (next.getType()) {
            case IF -> parseIfStatement();
            case LOOP -> parseLoopStatement();
            case WORD -> {
                Optional<StatementNode> stmtOpt = disambiguateStatement();
                if (stmtOpt.isPresent())
                    yield stmtOpt.get();
                else
                    throw syntaxError("Cannot disambiguate statement after WORD");
            }
            default -> throw syntaxError("Unexpected token in statement: " + next.getType());
        };
    }

    private IfNode parseIfStatement() throws SyntaxErrorException {
        tokenMgr.matchAndRemove(Token.TokenTypes.IF)
                .orElseThrow(() -> syntaxError("Expected 'if'"));
        ExpressionNode condition = parseExpression();
        requireLineBreak(); // Skip newlines after condition


        List<StatementNode> thenStmts;
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.INDENT) {
            thenStmts = parseStatements(true);  // stops when ELSE is reached
        } else {
            thenStmts = new ArrayList<>();
            thenStmts.add(parseStatement());
        }

        // Parse ELSE block (if present).
        Optional<ElseNode> elseOpt = Optional.empty();
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.ELSE) {
            tokenMgr.matchAndRemove(Token.TokenTypes.ELSE);
            requireLineBreak();
            List<StatementNode> elseStmts;
            if (tokenMgr.peek(0).isPresent() &&
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.INDENT) {
                // Simply call parseStatements() – it will process the block and remove the final DEDENT.
                elseStmts = parseStatements();
            } else {
                elseStmts = new ArrayList<>();
                elseStmts.add(parseStatement());
            }
            ElseNode elseNode = new ElseNode();
            elseNode.statements = elseStmts;
            elseOpt = Optional.of(elseNode);
        }

        IfNode ifNode = new IfNode();
        ifNode.condition = condition;
        ifNode.statements = thenStmts;
        ifNode.elseStatement = elseOpt;
        return ifNode;
    }

    // Loop = "loop" (VariableReference "=")? Expression NEWLINE Statements.
    private LoopNode parseLoopStatement() throws SyntaxErrorException {
        tokenMgr.matchAndRemove(Token.TokenTypes.LOOP)
                .orElseThrow(() -> syntaxError("Expected 'loop'"));
        Optional<VariableReferenceNode> assignOpt = Optional.empty();
        if (tokenMgr.peek(0).isPresent() && tokenMgr.peek(1).isPresent() &&
                tokenMgr.peek(1).get().getType() == Token.TokenTypes.ASSIGN) {
            Token varTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                    .orElseThrow(() -> syntaxError("Expected variable name before '='"));
            tokenMgr.matchAndRemove(Token.TokenTypes.ASSIGN)
                    .orElseThrow(() -> syntaxError("Expected '=' after variable name"));
            VariableReferenceNode varRef = new VariableReferenceNode();
            varRef.name = varTok.getValue();
            assignOpt = Optional.of(varRef);
        }
        ExpressionNode loopExpr = parseExpression();
        requireLineBreak();
        List<StatementNode> loopStmts;
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.INDENT) {
            loopStmts = parseStatements();
        } else {
            loopStmts = new ArrayList<>();
            loopStmts.add(parseStatement());
        }
        LoopNode loopNode = new LoopNode();
        loopNode.assignment = assignOpt;
        loopNode.expression = loopExpr;
        loopNode.statements = loopStmts;
        return loopNode;
    }

    private Optional<StatementNode> disambiguateStatement() throws SyntaxErrorException {
        Token first = tokenMgr.peek(0).get();
        if (tokenMgr.peek(1).isPresent()) {
            Token second = tokenMgr.peek(1).get();
            if (second.getType() == Token.TokenTypes.COMMA) {
                return Optional.of(parseMultipleAssignmentStatement());
            } else if (second.getType() == Token.TokenTypes.ASSIGN) {

                StatementNode stmt = parseAssignmentStatement();
                if (stmt instanceof AssignmentNode) {
                    AssignmentNode assign = (AssignmentNode) stmt;
                    if (assign.expression instanceof MethodCallExpressionNode) {
                        MethodCallExpressionNode mce = (MethodCallExpressionNode) assign.expression;


                        if (mce.objectName.isPresent() &&
                                (mce.objectName.get().equals("boolean") ||
                                        mce.objectName.get().equals("boolan"))) {
                            return Optional.of(stmt);
                        } else {
                            // Otherwise, wrap the method call into a method call statement node.
                            MethodCallStatementNode mcs = new MethodCallStatementNode(mce);
                            mcs.returnValues.add(assign.target);
                            return Optional.of(mcs);
                        }
                    }
                }
                return Optional.of(stmt);
            } else if (second.getType() == Token.TokenTypes.LPAREN ||
                    second.getType() == Token.TokenTypes.DOT) {
                MethodCallExpressionNode callExpr = parseMethodCallExpression();
                MethodCallStatementNode callStmt = new MethodCallStatementNode(callExpr);
                requireLineBreak();
                return Optional.of(callStmt);
            }
        }
        return Optional.empty();
    }

    // Assignment = VariableReference "=" Expression NEWLINE.
    private StatementNode parseAssignmentStatement() throws SyntaxErrorException {
        Token varTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected variable name for assignment"));
        VariableReferenceNode varRef = new VariableReferenceNode();
        varRef.name = varTok.getValue();
        tokenMgr.matchAndRemove(Token.TokenTypes.ASSIGN)
                .orElseThrow(() -> syntaxError("Expected '=' in assignment"));
        ExpressionNode expr = parseExpression();
        requireLineBreak();
        AssignmentNode assign = new AssignmentNode();
        assign.target = varRef;
        assign.expression = expr;
        return assign;

    }

    // MethodCall = (VariableReference ("," VariableReference)* "=")? MethodCallExpression NEWLINE.
    private StatementNode parseMultipleAssignmentStatement() throws SyntaxErrorException {
        List<VariableReferenceNode> varList = new ArrayList<>();
        varList.add(parseVariableReference());
        while (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.COMMA) {
            tokenMgr.matchAndRemove(Token.TokenTypes.COMMA);
            varList.add(parseVariableReference());
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.ASSIGN)
                .orElseThrow(() -> syntaxError("Expected '=' after multiple assignment variables"));
        ExpressionNode expr = parseExpression();
        if (!(expr instanceof MethodCallExpressionNode)) {
            throw syntaxError("Multiple assignment left-hand side must be assigned a method call expression");
        }
        MethodCallStatementNode mcs = new MethodCallStatementNode((MethodCallExpressionNode) expr);
        mcs.returnValues = varList;
        requireLineBreak();
        return mcs;
    }

    private VariableReferenceNode parseVariableReference() throws SyntaxErrorException {
        Token tok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected variable reference"));
        VariableReferenceNode varRef = new VariableReferenceNode();
        varRef.name = tok.getValue();
        return varRef;
    }

    private MethodCallExpressionNode parseMethodCallExpression() throws SyntaxErrorException {
        Token firstWord = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected method name or objectName"));
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.DOT) {
            tokenMgr.matchAndRemove(Token.TokenTypes.DOT);
            Token methodNameTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                    .orElseThrow(() -> syntaxError("Expected method name after '.'"));
            tokenMgr.matchAndRemove(Token.TokenTypes.LPAREN)
                    .orElseThrow(() -> syntaxError("Expected '(' after method name"));
            MethodCallExpressionNode callNode = new MethodCallExpressionNode();
            callNode.objectName = Optional.of(firstWord.getValue());
            callNode.methodName = methodNameTok.getValue();
            callNode.parameters = new ArrayList<>();
            if (tokenMgr.peek(0).isPresent() &&
                    tokenMgr.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
                callNode.parameters = parseExpressionList();
            }
            tokenMgr.matchAndRemove(Token.TokenTypes.RPAREN)
                    .orElseThrow(() -> syntaxError("Expected ')' after method call arguments"));
            return callNode;
        } else {
            tokenMgr.matchAndRemove(Token.TokenTypes.LPAREN)
                    .orElseThrow(() -> syntaxError("Expected '(' after method name"));
            MethodCallExpressionNode callNode = new MethodCallExpressionNode();
            callNode.objectName = Optional.empty();
            callNode.methodName = firstWord.getValue();
            callNode.parameters = new ArrayList<>();
            if (tokenMgr.peek(0).isPresent() &&
                    tokenMgr.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
                callNode.parameters = parseExpressionList();
            }
            tokenMgr.matchAndRemove(Token.TokenTypes.RPAREN)
                    .orElseThrow(() -> syntaxError("Expected ')' after method call arguments"));
            return callNode;
        }
    }

    // ExpressionList = Expression ("," Expression)*.
    private List<ExpressionNode> parseExpressionList() throws SyntaxErrorException {
        List<ExpressionNode> exprs = new ArrayList<>();
        exprs.add(parseExpression());
        while (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.COMMA) {
            tokenMgr.matchAndRemove(Token.TokenTypes.COMMA);
            exprs.add(parseExpression());
        }
        return exprs;
    }

    // Expression = Term ( ("+"|"-") Term )*.
    private ExpressionNode parseExpression() throws SyntaxErrorException {
        return parseComparison();
    }

    // Comparison = Expression ( "==" | "!=" | "<=" | ">=" | ">" | "<" ) Expression.
    private ExpressionNode parseComparison() throws SyntaxErrorException {
        ExpressionNode left = parseArithmetic();
        if (tokenMgr.peek(0).isPresent() &&
                isComparisonOperator(tokenMgr.peek(0).get().getType())) {
            Token opToken = tokenMgr.matchAndRemove(tokenMgr.peek(0).get().getType())
                    .orElseThrow(() -> syntaxError("Expected comparison operator"));
            ExpressionNode right = parseArithmetic();
            CompareNode cmp = new CompareNode();
            cmp.left = left;
            cmp.right = right;
            cmp.op = convertComparisonOperator(opToken.getType());
            return cmp;
        }
        return left;
    }

    // Arithmetic = Term ( ("+"|"-") Term )*.
    private ExpressionNode parseArithmetic() throws SyntaxErrorException {
        ExpressionNode left = parseTerm();
        while (tokenMgr.peek(0).isPresent()) {
            Token.TokenTypes ttype = tokenMgr.peek(0).get().getType();
            if (ttype == Token.TokenTypes.PLUS || ttype == Token.TokenTypes.MINUS) {
                Token op = tokenMgr.matchAndRemove(ttype)
                        .orElseThrow(() -> syntaxError("Expected '+' or '-'"));
                ExpressionNode right = parseTerm();
                MathOpNode mathNode = new MathOpNode();
                mathNode.left = left;
                mathNode.right = right;
                mathNode.op = (op.getType() == Token.TokenTypes.PLUS)
                        ? MathOpNode.MathOperations.add
                        : MathOpNode.MathOperations.subtract;
                left = mathNode;
            } else {
                break;
            }
        }
        return left;
    }

    // Term = Factor ( ("*"|"/"|"%") Factor )*.
    private ExpressionNode parseTerm() throws SyntaxErrorException {
        ExpressionNode left = parseFactor();
        while (tokenMgr.peek(0).isPresent()) {
            Token.TokenTypes ttype = tokenMgr.peek(0).get().getType();
            if (ttype == Token.TokenTypes.TIMES ||
                    ttype == Token.TokenTypes.DIVIDE ||
                    ttype == Token.TokenTypes.MODULO) {
                Token op = tokenMgr.matchAndRemove(ttype)
                        .orElseThrow(() -> syntaxError("Expected '*' or '/' or '%'"));
                ExpressionNode right = parseFactor();
                MathOpNode mathNode = new MathOpNode();
                mathNode.left = left;
                mathNode.right = right;
                switch (op.getType()) {
                    case TIMES -> mathNode.op = MathOpNode.MathOperations.multiply;
                    case DIVIDE -> mathNode.op = MathOpNode.MathOperations.divide;
                    case MODULO -> mathNode.op = MathOpNode.MathOperations.modulo;
                    default -> throw syntaxError("Unrecognized term operator");
                }
                left = mathNode;
            } else {
                break;
            }
        }
        return left;
    }

    // Factor = NUMBER | VariableReference | STRINGLITERAL | CHARACTERLITERAL | MethodCallExpression
    //        | "(" Expression ")" | "new" IDENTIFIER "(" (Expression ("," Expression)*)? ")".
    private ExpressionNode parseFactor() throws SyntaxErrorException {
        Token token = tokenMgr.peek(0)
                .orElseThrow(() -> syntaxError("Expected expression token"));
        switch (token.getType()) {
            case NUMBER -> {
                tokenMgr.matchAndRemove(Token.TokenTypes.NUMBER);
                NumericLiteralNode numNode = new NumericLiteralNode();
                numNode.value = (float) Double.parseDouble(token.getValue());
                return numNode;
            }
            case QUOTEDSTRING -> {
                tokenMgr.matchAndRemove(Token.TokenTypes.QUOTEDSTRING);
                StringLiteralNode strNode = new StringLiteralNode();
                strNode.value = stripQuotes(token.getValue());
                return strNode;
            }
            case QUOTEDCHARACTER -> {
                tokenMgr.matchAndRemove(Token.TokenTypes.QUOTEDCHARACTER);
                CharLiteralNode charNode = new CharLiteralNode();
                charNode.value = extractCharLiteral(token.getValue());
                return charNode;
            }
            case LPAREN -> {
                tokenMgr.matchAndRemove(Token.TokenTypes.LPAREN);
                ExpressionNode expr = parseExpression();
                tokenMgr.matchAndRemove(Token.TokenTypes.RPAREN)
                        .orElseThrow(() -> syntaxError("Expected ')'"));
                return expr;
            }
            case NEW -> {
                tokenMgr.matchAndRemove(Token.TokenTypes.NEW);
                return parseNewExpression();
            }
            case WORD -> {
                String wordVal = token.getValue();
                if ("true".equals(wordVal) || "false".equals(wordVal)) {
                    tokenMgr.matchAndRemove(Token.TokenTypes.WORD);
                    return new BooleanLiteralNode("true".equals(wordVal));
                } else {
                    return parsePossibleVarRefOrMethodCall();
                }
            }
            default ->
                    throw syntaxError("Unexpected token in factor: " + token.getType());
        }
    }



    // "new" IDENTIFIER "(" (Expression ("," Expression)*)? ")".
    private NewNode parseNewExpression() throws SyntaxErrorException {
        Token classNameTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected class name after 'new'"));
        NewNode newNode = new NewNode();
        newNode.className = classNameTok.getValue();
        tokenMgr.matchAndRemove(Token.TokenTypes.LPAREN)
                .orElseThrow(() -> syntaxError("Expected '(' after class name"));
        List<ExpressionNode> args = new ArrayList<>();
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
            args = parseExpressionList();
        }
        tokenMgr.matchAndRemove(Token.TokenTypes.RPAREN)
                .orElseThrow(() -> syntaxError("Expected ')' to close new expression"));
        newNode.parameters = args;
        return newNode;
    }

    private ExpressionNode parsePossibleVarRefOrMethodCall() throws SyntaxErrorException {
        Token firstWord = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                .orElseThrow(() -> syntaxError("Expected identifier"));
        if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.DOT) {
            tokenMgr.matchAndRemove(Token.TokenTypes.DOT);
            Token methodNameTok = tokenMgr.matchAndRemove(Token.TokenTypes.WORD)
                    .orElseThrow(() -> syntaxError("Expected method name after '.'"));
            while (tokenMgr.peek(0).isPresent() &&
                    (tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE ||
                            tokenMgr.peek(0).get().getType() == Token.TokenTypes.INDENT)) {
                tokenMgr.matchAndRemove(tokenMgr.peek(0).get().getType());
            }
            if (tokenMgr.peek(0).isPresent() &&
                    tokenMgr.peek(0).get().getType() == Token.TokenTypes.LPAREN) {
                tokenMgr.matchAndRemove(Token.TokenTypes.LPAREN)
                        .orElseThrow(() -> syntaxError("Expected '(' after method name"));
                MethodCallExpressionNode callNode = new MethodCallExpressionNode();
                callNode.objectName = Optional.of(firstWord.getValue());
                callNode.methodName = methodNameTok.getValue();
                callNode.parameters = new ArrayList<>();
                if (tokenMgr.peek(0).isPresent() && tokenMgr.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
                    callNode.parameters = parseExpressionList();
                }
                tokenMgr.matchAndRemove(Token.TokenTypes.RPAREN)
                        .orElseThrow(() -> syntaxError("Expected ')' after method call arguments"));
                return callNode;
            } else {

                MethodCallExpressionNode callNode = new MethodCallExpressionNode();
                callNode.objectName = Optional.of(firstWord.getValue());
                callNode.methodName = methodNameTok.getValue();
                callNode.parameters = new ArrayList<>();
                return callNode;
            }
        } else if (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.LPAREN) {
            while (tokenMgr.peek(0).isPresent() &&
                    (tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE ||
                            tokenMgr.peek(0).get().getType() == Token.TokenTypes.INDENT)) {
                tokenMgr.matchAndRemove(tokenMgr.peek(0).get().getType());
            }
            tokenMgr.matchAndRemove(Token.TokenTypes.LPAREN)
                    .orElseThrow(() -> syntaxError("Expected '(' after method name"));
            MethodCallExpressionNode callNode = new MethodCallExpressionNode();
            callNode.objectName = Optional.empty();
            callNode.methodName = firstWord.getValue();
            callNode.parameters = new ArrayList<>();
            if (tokenMgr.peek(0).isPresent() && tokenMgr.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
                callNode.parameters = parseExpressionList();
            }
            tokenMgr.matchAndRemove(Token.TokenTypes.RPAREN)
                    .orElseThrow(() -> syntaxError("Expected ')' after method call arguments"));
            return callNode;
        } else {
            VariableReferenceNode varRef = new VariableReferenceNode();
            varRef.name = firstWord.getValue();
            return varRef;
        }
    }


    private boolean isReservedType(String t) {
        return t.equals("number") ||
                t.equals("string") ||
                t.equals("boolean") ||
                t.equals("character");
    }

    private boolean isComparisonOperator(Token.TokenTypes type) {
        return switch (type) {
            case EQUAL, NOTEQUAL, LESSTHAN, LESSTHANEQUAL, GREATERTHAN, GREATERTHANEQUAL -> true;
            default -> false;
        };
    }

    private CompareNode.CompareOperations convertComparisonOperator(Token.TokenTypes type)
            throws SyntaxErrorException {
        return switch (type) {
            case EQUAL -> CompareNode.CompareOperations.eq;
            case NOTEQUAL -> CompareNode.CompareOperations.ne;
            case LESSTHAN -> CompareNode.CompareOperations.lt;
            case LESSTHANEQUAL -> CompareNode.CompareOperations.le;
            case GREATERTHAN -> CompareNode.CompareOperations.gt;
            case GREATERTHANEQUAL -> CompareNode.CompareOperations.ge;
            default -> throw syntaxError("Invalid comparison operator: " + type);
        };
    }

    private String stripQuotes(String quoted) {
        if (quoted.length() >= 2 && quoted.startsWith("\"") && quoted.endsWith("\"")) {
            return quoted.substring(1, quoted.length() - 1);
        }
        return quoted;
    }

    private char extractCharLiteral(String literal) {
        if (literal.length() >= 2 && literal.startsWith("'") && literal.endsWith("'")) {
            return literal.charAt(1);
        }
        return literal.charAt(0);
    }

    private void requireLineBreak() throws SyntaxErrorException {
        while (tokenMgr.peek(0).isPresent() &&
                tokenMgr.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
            tokenMgr.matchAndRemove(Token.TokenTypes.NEWLINE);
        }
    }


    private SyntaxErrorException syntaxError(String msg) {
        return new SyntaxErrorException(msg, tokenMgr.getCurrentLine(), tokenMgr.getCurrentColumnNumber());
    }
}
