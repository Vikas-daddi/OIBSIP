package com.oasisinfobyte.calculator;

import java.math.BigDecimal;

/** Evaluates arithmetic expressions with operator precedence and grouping parentheses. */
final class CalculatorExpression {
    private final String source;
    private int position;

    private CalculatorExpression(String source) {
        this.source = source.replace('×', '*').replace('÷', '/').replace('−', '-');
    }

    static double evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty() || expression.length() > 256) {
            throw new IllegalArgumentException("Enter a valid expression");
        }
        CalculatorExpression parser = new CalculatorExpression(expression);
        double value = parser.parseExpression();
        if (parser.position != parser.source.length() || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid expression");
        }
        return value;
    }

    static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Result is not finite");
        }
        if (value == 0d) return "0";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private double parseExpression() {
        double value = parseTerm();
        while (position < source.length()) {
            char operator = source.charAt(position);
            if (operator != '+' && operator != '-') break;
            position++;
            double right = parseTerm();
            value = operator == '+' ? value + right : value - right;
        }
        return value;
    }

    private double parseTerm() {
        double value = parseUnary();
        while (position < source.length()) {
            char operator = source.charAt(position);
            if (operator != '*' && operator != '/') break;
            position++;
            double right = parseUnary();
            if (operator == '/' && right == 0d) {
                throw new ArithmeticException("Cannot divide by zero");
            }
            value = operator == '*' ? value * right : value / right;
        }
        return value;
    }

    private double parseUnary() {
        if (position < source.length() && (source.charAt(position) == '+' || source.charAt(position) == '-')) {
            char sign = source.charAt(position++);
            double value = parseUnary();
            return sign == '-' ? -value : value;
        }
        return parsePrimary();
    }

    private double parsePrimary() {
        if (position < source.length() && source.charAt(position) == '(') {
            position++;
            double value = parseExpression();
            if (position >= source.length() || source.charAt(position) != ')') {
                throw new IllegalArgumentException("Closing parenthesis is missing");
            }
            position++;
            return value;
        }
        return parseNumber();
    }

    private double parseNumber() {
        int start = position;
        boolean decimalSeen = false;
        while (position < source.length()) {
            char current = source.charAt(position);
            if (current >= '0' && current <= '9') {
                position++;
            } else if (current == '.' && !decimalSeen) {
                decimalSeen = true;
                position++;
            } else {
                break;
            }
        }
        if (position == start || (position == start + 1 && source.charAt(start) == '.')) {
            throw new IllegalArgumentException("A number is missing");
        }
        try {
            return Double.parseDouble(source.substring(start, position));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number", exception);
        }
    }
}
