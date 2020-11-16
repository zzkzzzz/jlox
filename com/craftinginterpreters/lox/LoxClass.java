package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

class LoxClass implements LoxCallable {
    final String name;

    LoxClass(String name) {
        this.name = name;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // everytime you call the class, it will return a instance of it
        LoxInstance instance = new LoxInstance(this);
        return instance;
    }

    @Override
    public int arity() {
        return 0;
    }

    @Override
    public String toString() {
        return name;
    }
}