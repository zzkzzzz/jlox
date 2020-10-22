package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    // The key of the hashmap is String not token
    // A token represents a unit of code at a specific place in the source text, but
    // when it comes to looking up variables, all identifier tokens with the same
    // name should refer to the same variable
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    // global scope
    Environment() {
        enclosing = null;
    }

    // local scope nestd insdie given outer one
    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        // if cant find the variable in this scope
        // then try to find it in the enclosing scope(outer)
        if (enclosing != null)
            return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        // if cant find the variable in this scope
        // then try to find it in the enclosing scope(outer)
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

}
