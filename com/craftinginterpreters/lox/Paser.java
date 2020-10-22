package com.craftinginterpreters.lox;

import java.util.List;
import java.util.ArrayList;

import static com.craftinginterpreters.lox.TokenType.*;

// program        → statement* EOF 

// declaration    → varDecl | statement 
// statement      → exprStmt | printStmt | block

// varDecl        → "var" IDENTIFIER ( "=" expression )? ";" 

// exprStmt       → expression ";" 
// printStmt      → "print" expression ";" 
// block          → "{" declaration* "}" 

// expression     → assignment 
// assignment     → IDENTIFIER "=" assignment | equality 
// equality       → comparison ( ( "!=" | "==" ) comparison )* 
// comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )*
// term           → factor ( ( "-" | "+" ) factor )* 
// factor         → unary ( ( "/" | "*" ) unary )* 
// unary          → ( "!" | "-" ) unary | primary 
// primary        → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" | IDENTIFIER 

/**
 * Recursive Descent Parsing -> Top-down parser. Given a sequence of tokens,
 * detect any errors and tell the user about their mistakes. Or produce a
 * corresponding syntax tree.
 */
class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    // point to next token waiting to be parsed
    private int current = 0;

    /**
     * Paser Constructor
     * 
     * @param tokens list of input tokens
     */
    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Parse a statement and return it
     * 
     * @return
     */
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(VAR))
                return varDeclaration();

            return statement();
        } catch (ParseError error) {
            // error recovery
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        // We’ve already matched the var token, so next it requires and consumes an
        // identifier token for the variable name.
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        // then check '=' token. Otherwise, leave the initializer as "null"
        if (match(EQUAL)) {
            // expression after the '='
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(PRINT))
            return printStatement();
        if (match(LEFT_BRACE))
            return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    // equality → comparison ( ( "!=" | "==" ) comparison )*
    private Expr equality() {
        Expr expr = comparison();

        // if the parser never encounters an equality operator, then it never enters the
        // loop. Then calls and returns comparison().
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )*
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // term → factor ( ( "-" | "+" ) factor )*
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // factor → unary ( ( "/" | "*" ) unary )*
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // unary → ( "!" | "-" ) unary | primary
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            // recursively call unary() again to parse the operand
            // example: (-5)+(-4)+(-1)
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    // primary → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" |
    // IDENTIFIER
    private Expr primary() {
        if (match(FALSE))
            return new Expr.Literal(false);

        if (match(TRUE))
            return new Expr.Literal(true);

        if (match(NIL))
            return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        // left parentheses "(" must match right parentheses ")"
        // otherwise, error msg => "Expect ')' after expression."
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    /**
     * check for given TokenTypes
     * 
     * @param types
     * @return
     */
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type))
            return advance();

        throw error(peek(), message);
    }

    /**
     * checks to see if the current token has any of the given types. If so, it
     * consumes the token and returns true. Otherwise, it returns false and leaves
     * the current token alone.
     * 
     * @param type TokenType
     * @return
     */
    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().type == type;
    }

    /**
     * consume the token
     * 
     * @return token
     */
    private Token advance() {
        if (!isAtEnd())
            current++;
        return previous();
    }

    /**
     * check if run out of tokens to parse
     * 
     * @return boolean
     */
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    /**
     * peek the current token
     * 
     * @return current token
     */
    private Token peek() {
        return tokens.get(current);
    }

    /**
     * return the most recently token
     * 
     * @return previous token
     */
    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    // gets it back to trying to parse the beginning of the next statement or
    // declaration.
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON)
                return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

}
