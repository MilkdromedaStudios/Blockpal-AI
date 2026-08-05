package com.milkdromeda.blockpal.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>The little language the AI writes.</b>
 *
 * <p>Instead of emitting a fixed JSON action list, the model writes a short program
 * that presses the bot's inputs — walk, turn, hold left-click, tap right-click — and
 * can branch and loop on what it finds. That is what makes the behaviour feel earned:
 * the model has to think about <i>how</i> to do something with the same controls a
 * player has, rather than naming an outcome and having the mod conjure it.
 *
 * <pre>{@code
 * # chop the tree in front of me
 * lookAt(120, 71, -43)
 * goTo(120, 70, -41)
 * repeat 5 {
 *   mine()
 *   if blockAt(120, y() + 1, -43) == "air" { break }
 * }
 * collect(6)
 * say("Got the wood!")
 * }</pre>
 *
 * <p>Scripts are <b>compiled to a flat list of ops with jumps</b> rather than walked as
 * a tree. That matters for a game loop: a program counter can be suspended and resumed
 * across ticks, so a script that walks for six seconds simply parks on one op and the
 * server keeps ticking normally. See {@link ScriptRunner} for the machine.
 *
 * <p>Everything is bounded — program size, loop nesting, ops per tick and total run
 * time — so a model that writes {@code while true {}} costs one wasted bot, not a
 * hung server.
 */
public final class AgentScript {

    private AgentScript() {}

    /** Hard ceiling on compiled program size, so a runaway generation can't eat memory. */
    public static final int MAX_OPS = 4000;
    /** How deep {@code repeat}/{@code while}/{@code if} may nest. */
    public static final int MAX_DEPTH = 16;

    // ── ops ─────────────────────────────────────────────────────────────────────

    public enum OpCode {
        PUSH,        // operand: a constant value
        LOAD,        // operand: variable name
        STORE,       // operand: variable name
        CALL,        // operand: function name, arg: argument count
        BINOP,       // operand: operator symbol
        NEG, NOT,
        JUMP,        // arg: absolute target
        JUMP_IF_FALSE,
        POP,
        HALT
    }

    /** One compiled instruction. {@code operand} is a constant/name; {@code arg} an int. */
    public record Op(OpCode code, Object operand, int arg) {
        static Op of(OpCode c) { return new Op(c, null, 0); }
        static Op of(OpCode c, Object o) { return new Op(c, o, 0); }
        static Op of(OpCode c, Object o, int a) { return new Op(c, o, a); }
    }

    /** A compiled script, ready for {@link ScriptRunner}. */
    public record Program(List<Op> ops, String source) {
        public int size() { return ops.size(); }
    }

    /** Thrown when a script can't be understood; the message is shown to the AI so it can fix it. */
    public static class ScriptException extends RuntimeException {
        public ScriptException(String message) { super(message); }
    }

    // ── lexer ───────────────────────────────────────────────────────────────────

    private enum T { NUMBER, STRING, IDENT, OP, EOF }

    private record Token(T type, String text, double number, int line) {}

    private static List<Token> lex(String src) {
        List<Token> out = new ArrayList<>();
        int i = 0, line = 1;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') { line++; i++; continue; }
            if (Character.isWhitespace(c) || c == ';') { i++; continue; }
            // Comments: # ... and // ... to end of line.
            if (c == '#' || (c == '/' && i + 1 < n && src.charAt(i + 1) == '/')) {
                while (i < n && src.charAt(i) != '\n') i++;
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                int start = i;
                while (i < n && (Character.isDigit(src.charAt(i)) || src.charAt(i) == '.')) i++;
                try {
                    out.add(new Token(T.NUMBER, src.substring(start, i),
                            Double.parseDouble(src.substring(start, i)), line));
                } catch (NumberFormatException e) {
                    throw new ScriptException("line " + line + ": '" + src.substring(start, i)
                            + "' is not a number");
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c;
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && src.charAt(i) != quote) {
                    char ch = src.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        i++;
                        char esc = src.charAt(i);
                        sb.append(switch (esc) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            default -> esc;
                        });
                    } else {
                        if (ch == '\n') line++;
                        sb.append(ch);
                    }
                    i++;
                }
                if (i >= n) throw new ScriptException("line " + line + ": unterminated text string");
                i++;   // closing quote
                out.add(new Token(T.STRING, sb.toString(), 0, line));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_'
                        || src.charAt(i) == ':' || src.charAt(i) == '.')) i++;
                out.add(new Token(T.IDENT, src.substring(start, i), 0, line));
                continue;
            }
            // Two-character operators first so <= doesn't lex as < then =.
            if (i + 1 < n) {
                String two = src.substring(i, i + 2);
                if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")
                        || two.equals("&&") || two.equals("||")) {
                    out.add(new Token(T.OP, two, 0, line));
                    i += 2;
                    continue;
                }
            }
            if ("+-*/%<>=!(){},".indexOf(c) >= 0) {
                out.add(new Token(T.OP, String.valueOf(c), 0, line));
                i++;
                continue;
            }
            throw new ScriptException("line " + line + ": I don't understand the character '" + c + "'");
        }
        out.add(new Token(T.EOF, "", 0, line));
        return out;
    }

    // ── parser / compiler ───────────────────────────────────────────────────────

    /** Parses and compiles {@code source}. Throws {@link ScriptException} with a readable reason. */
    public static Program compile(String source) {
        if (source == null || source.isBlank()) throw new ScriptException("the script was empty");
        return new Compiler(lex(source), source).compile();
    }

    private static final class Compiler {
        private final List<Token> tokens;
        private final String source;
        private final List<Op> ops = new ArrayList<>();
        private int pos;
        private int depth;
        /** Patch lists for the innermost loop, so break/continue know where to jump. */
        private final List<List<Integer>> breakStack = new ArrayList<>();
        private final List<List<Integer>> continueStack = new ArrayList<>();
        private int loopCounter;

        Compiler(List<Token> tokens, String source) {
            this.tokens = tokens;
            this.source = source;
        }

        Program compile() {
            while (!check(T.EOF)) statement();
            emit(Op.of(OpCode.HALT));
            if (ops.size() > MAX_OPS) {
                throw new ScriptException("that script is too long (" + ops.size()
                        + " steps, limit " + MAX_OPS + ") — keep it short and re-plan after looking again");
            }
            return new Program(List.copyOf(ops), source);
        }

        // -- statements --

        private void statement() {
            if (matchIdent("let")) {
                Token name = expect(T.IDENT, "a variable name after 'let'");
                expectOp("=", "'=' after the variable name");
                expression();
                emit(Op.of(OpCode.STORE, name.text()));
                return;
            }
            if (matchIdent("if")) { ifStatement(); return; }
            if (matchIdent("repeat")) { repeatStatement(); return; }
            if (matchIdent("while")) { whileStatement(); return; }
            if (matchIdent("break")) {
                if (breakStack.isEmpty()) throw new ScriptException("'break' outside a loop");
                breakStack.get(breakStack.size() - 1).add(emit(Op.of(OpCode.JUMP, null, -1)));
                return;
            }
            if (matchIdent("continue")) {
                if (continueStack.isEmpty()) throw new ScriptException("'continue' outside a loop");
                continueStack.get(continueStack.size() - 1).add(emit(Op.of(OpCode.JUMP, null, -1)));
                return;
            }
            // Bare expression (nearly always an action call) — discard its value.
            expression();
            emit(Op.of(OpCode.POP));
        }

        private void ifStatement() {
            expression();
            int jumpFalse = emit(Op.of(OpCode.JUMP_IF_FALSE, null, -1));
            block();
            if (matchIdent("else")) {
                int jumpEnd = emit(Op.of(OpCode.JUMP, null, -1));
                patch(jumpFalse, ops.size());
                if (matchIdent("if")) ifStatement(); else block();
                patch(jumpEnd, ops.size());
            } else {
                patch(jumpFalse, ops.size());
            }
        }

        private void repeatStatement() {
            // repeat N { body }  →  hidden counter, decremented each pass.
            String counter = "$repeat" + (loopCounter++);
            expression();
            emit(Op.of(OpCode.STORE, counter));
            int top = ops.size();
            emit(Op.of(OpCode.LOAD, counter));
            emit(Op.of(OpCode.PUSH, 0.0));
            emit(Op.of(OpCode.BINOP, ">"));
            int exit = emit(Op.of(OpCode.JUMP_IF_FALSE, null, -1));
            enterLoop();
            block();
            int continueTarget = ops.size();
            emit(Op.of(OpCode.LOAD, counter));
            emit(Op.of(OpCode.PUSH, 1.0));
            emit(Op.of(OpCode.BINOP, "-"));
            emit(Op.of(OpCode.STORE, counter));
            emit(Op.of(OpCode.JUMP, null, top));
            patch(exit, ops.size());
            exitLoop(continueTarget, ops.size());
        }

        private void whileStatement() {
            int top = ops.size();
            expression();
            int exit = emit(Op.of(OpCode.JUMP_IF_FALSE, null, -1));
            enterLoop();
            block();
            emit(Op.of(OpCode.JUMP, null, top));
            patch(exit, ops.size());
            exitLoop(top, ops.size());
        }

        private void enterLoop() {
            breakStack.add(new ArrayList<>());
            continueStack.add(new ArrayList<>());
        }

        private void exitLoop(int continueTarget, int breakTarget) {
            for (int idx : breakStack.remove(breakStack.size() - 1)) patch(idx, breakTarget);
            for (int idx : continueStack.remove(continueStack.size() - 1)) patch(idx, continueTarget);
        }

        private void block() {
            if (++depth > MAX_DEPTH) throw new ScriptException("the script nests too deeply");
            expectOp("{", "'{' to start a block");
            while (!checkOp("}")) {
                if (check(T.EOF)) throw new ScriptException("a '{' block was never closed with '}'");
                statement();
            }
            expectOp("}", "'}'");
            depth--;
        }

        // -- expressions (precedence climbing) --

        private void expression() { or(); }

        private void or() {
            and();
            while (matchOp("||")) { and(); emit(Op.of(OpCode.BINOP, "||")); }
        }

        private void and() {
            comparison();
            while (matchOp("&&")) { comparison(); emit(Op.of(OpCode.BINOP, "&&")); }
        }

        private void comparison() {
            addition();
            while (checkOp("==") || checkOp("!=") || checkOp("<") || checkOp(">")
                    || checkOp("<=") || checkOp(">=")) {
                String op = advance().text();
                addition();
                emit(Op.of(OpCode.BINOP, op));
            }
        }

        private void addition() {
            multiplication();
            while (checkOp("+") || checkOp("-")) {
                String op = advance().text();
                multiplication();
                emit(Op.of(OpCode.BINOP, op));
            }
        }

        private void multiplication() {
            unary();
            while (checkOp("*") || checkOp("/") || checkOp("%")) {
                String op = advance().text();
                unary();
                emit(Op.of(OpCode.BINOP, op));
            }
        }

        private void unary() {
            if (matchOp("-")) { unary(); emit(Op.of(OpCode.NEG)); return; }
            if (matchOp("!")) { unary(); emit(Op.of(OpCode.NOT)); return; }
            primary();
        }

        private void primary() {
            Token t = peek();
            if (t.type() == T.NUMBER) { advance(); emit(Op.of(OpCode.PUSH, t.number())); return; }
            if (t.type() == T.STRING) { advance(); emit(Op.of(OpCode.PUSH, t.text())); return; }
            if (matchOp("(")) {
                expression();
                expectOp(")", "')'");
                return;
            }
            if (t.type() == T.IDENT) {
                advance();
                String name = t.text();
                if ("true".equalsIgnoreCase(name)) { emit(Op.of(OpCode.PUSH, Boolean.TRUE)); return; }
                if ("false".equalsIgnoreCase(name)) { emit(Op.of(OpCode.PUSH, Boolean.FALSE)); return; }
                if (matchOp("(")) {
                    int argc = 0;
                    if (!checkOp(")")) {
                        do { expression(); argc++; } while (matchOp(","));
                    }
                    expectOp(")", "')' to close the arguments");
                    String fn = name.toLowerCase(Locale.ROOT);
                    if (!BotApi.isFunction(fn)) {
                        throw new ScriptException("there is no action called '" + name + "'. "
                                + "Use only the actions listed in the API.");
                    }
                    // Arity is checked here, not at run time: a model that miscounts
                    // arguments should be told before the bot starts acting on the script.
                    String arityProblem = BotApi.checkArity(fn, argc);
                    if (arityProblem != null) {
                        throw new ScriptException("line " + t.line() + ": " + arityProblem);
                    }
                    emit(Op.of(OpCode.CALL, fn, argc));
                    return;
                }
                emit(Op.of(OpCode.LOAD, name));
                return;
            }
            throw new ScriptException("line " + t.line() + ": unexpected '" + t.text() + "'");
        }

        // -- token helpers --

        private int emit(Op op) { ops.add(op); return ops.size() - 1; }

        private void patch(int index, int target) {
            Op old = ops.get(index);
            ops.set(index, new Op(old.code(), old.operand(), target));
        }

        private Token peek() { return tokens.get(pos); }
        private Token advance() { return tokens.get(pos++); }
        private boolean check(T type) { return peek().type() == type; }

        private boolean checkOp(String text) {
            return peek().type() == T.OP && peek().text().equals(text);
        }

        private boolean matchOp(String text) {
            if (checkOp(text)) { pos++; return true; }
            return false;
        }

        private boolean matchIdent(String word) {
            if (peek().type() == T.IDENT && peek().text().equalsIgnoreCase(word)) { pos++; return true; }
            return false;
        }

        private Token expect(T type, String what) {
            if (!check(type)) {
                throw new ScriptException("line " + peek().line() + ": expected " + what
                        + " but found '" + peek().text() + "'");
            }
            return advance();
        }

        private void expectOp(String text, String what) {
            if (!matchOp(text)) {
                throw new ScriptException("line " + peek().line() + ": expected " + what
                        + " but found '" + peek().text() + "'");
            }
        }
    }
}
