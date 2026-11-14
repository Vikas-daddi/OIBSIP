package com.oasisinfobyte.calculator;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

/** Main screen and input controller for the calculator. */
public final class MainActivity extends Activity implements View.OnClickListener {
    private static final String STATE_EXPRESSION = "calculator.expression";
    private static final String STATE_RESULT = "calculator.result";
    private static final String STATE_JUST_EVALUATED = "calculator.justEvaluated";

    private TextView expressionDisplay;
    private TextView resultDisplay;
    private StringBuilder expression = new StringBuilder();
    private String shownResult = "0";
    private boolean justEvaluated;
    private boolean showingError;
    private SelfUpdateManager selfUpdateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        expressionDisplay = findViewById(R.id.expressionDisplay);
        resultDisplay = findViewById(R.id.resultDisplay);
        selfUpdateManager = new SelfUpdateManager(this);

        GridLayout keypad = findViewById(R.id.keypad);
        for (int index = 0; index < keypad.getChildCount(); index++) {
            keypad.getChildAt(index).setOnClickListener(this);
        }

        if (savedInstanceState != null) {
            expression = new StringBuilder(savedInstanceState.getString(STATE_EXPRESSION, ""));
            shownResult = savedInstanceState.getString(STATE_RESULT, "0");
            justEvaluated = savedInstanceState.getBoolean(STATE_JUST_EVALUATED, false);
        }
        render();
        selfUpdateManager.checkForUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selfUpdateManager != null) selfUpdateManager.onResume();
    }

    @Override
    protected void onPause() {
        if (selfUpdateManager != null) selfUpdateManager.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (selfUpdateManager != null) selfUpdateManager.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_EXPRESSION, expression.toString());
        outState.putString(STATE_RESULT, shownResult);
        outState.putBoolean(STATE_JUST_EVALUATED, justEvaluated);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View view) {
        if (!(view instanceof Button)) return;
        String key = ((Button) view).getText().toString();

        if ("C".equals(key)) {
            clear();
        } else if ("DEL".equals(key)) {
            backspace();
        } else if ("=".equals(key)) {
            calculate();
        } else if ("±".equals(key)) {
            toggleSign();
        } else if ("%".equals(key)) {
            applyPercent();
        } else if (isOperator(key)) {
            appendOperator(key);
        } else if (".".equals(key)) {
            appendDecimal();
        } else if (isDigit(key)) {
            appendDigit(key);
        }
        render();
    }

    private void clear() {
        expression.setLength(0);
        shownResult = "0";
        justEvaluated = false;
        showingError = false;
    }

    private void backspace() {
        if (showingError) {
            clear();
            return;
        }
        if (justEvaluated) {
            expression.setLength(0);
            justEvaluated = false;
        }
        if (expression.length() > 0) {
            expression.deleteCharAt(expression.length() - 1);
        }
        shownResult = currentEntry();
    }

    private void appendDigit(String digit) {
        beginFreshEntryIfNeeded();
        if (expression.length() >= 128) return;
        if (endsWithOperator()) {
            expression.append(digit);
        } else {
            int start = currentNumberStart();
            String current = expression.substring(start);
            if ("0".equals(current)) {
                expression.replace(start, expression.length(), digit);
            } else if ("-0".equals(current)) {
                expression.replace(start, expression.length(), "-" + digit);
            } else {
                expression.append(digit);
            }
        }
        shownResult = currentEntry();
    }

    private void appendDecimal() {
        beginFreshEntryIfNeeded();
        if (expression.length() >= 128) return;
        if (endsWithOperator() || expression.length() == 0) {
            expression.append("0.");
        } else {
            String current = currentEntry();
            if (!current.contains(".")) expression.append('.');
        }
        shownResult = currentEntry();
    }

    private void appendOperator(String operator) {
        if (showingError) clear();
        if (justEvaluated) {
            expression.append(shownResult);
            justEvaluated = false;
        }
        if (expression.length() == 0) {
            if ("−".equals(operator)) expression.append('-');
            return;
        }
        char last = expression.charAt(expression.length() - 1);
        if (isOperatorChar(last)) {
            if (expression.length() == 1 && last == '-') return;
            expression.setCharAt(expression.length() - 1, toAsciiOperator(operator));
        } else if (last != '.') {
            expression.append(toAsciiOperator(operator));
        }
        justEvaluated = false;
    }

    private void calculate() {
        if (showingError || expression.length() == 0 || endsWithOperator()
                || expression.charAt(expression.length() - 1) == '.') return;
        String completedExpression = expression.toString();
        try {
            shownResult = CalculatorExpression.format(CalculatorExpression.evaluate(completedExpression));
            expressionDisplay.setText(completedExpression.replace('*', '×').replace('/', '÷').replace('-', '−'));
            expression.setLength(0);
            justEvaluated = true;
            showingError = false;
        } catch (ArithmeticException exception) {
            showError("Cannot divide by zero");
        } catch (IllegalArgumentException exception) {
            showError("Invalid expression");
        }
    }

    private void applyPercent() {
        if (showingError) clear();
        if (justEvaluated) {
            try {
                shownResult = CalculatorExpression.format(Double.parseDouble(shownResult) / 100d);
            } catch (NumberFormatException exception) {
                clear();
            }
            justEvaluated = true;
            return;
        }
        if (expression.length() == 0 || endsWithOperator()) return;
        int start = currentNumberStart();
        try {
            double number = Double.parseDouble(expression.substring(start));
            expression.replace(start, expression.length(), CalculatorExpression.format(number / 100d));
            shownResult = currentEntry();
        } catch (NumberFormatException exception) {
            showError("Invalid expression");
        }
    }

    private void toggleSign() {
        if (showingError) clear();
        if (justEvaluated) {
            try {
                double value = -Double.parseDouble(shownResult);
                shownResult = CalculatorExpression.format(value);
            } catch (NumberFormatException exception) {
                clear();
            }
            justEvaluated = true;
            return;
        }
        if (expression.length() == 0 || endsWithOperator()) return;
        int start = currentNumberStart();
        if (start < expression.length() && expression.charAt(start) == '-') {
            expression.deleteCharAt(start);
        } else {
            expression.insert(start, '-');
        }
        shownResult = currentEntry();
    }

    private void beginFreshEntryIfNeeded() {
        if (showingError) clear();
        if (justEvaluated) {
            expression.setLength(0);
            justEvaluated = false;
        }
    }

    private boolean endsWithOperator() {
        return expression.length() > 0 && isOperatorChar(expression.charAt(expression.length() - 1));
    }

    private int currentNumberStart() {
        int index = expression.length() - 1;
        while (index >= 0) {
            char value = expression.charAt(index);
            if ((value >= '0' && value <= '9') || value == '.') index--;
            else break;
        }
        if (index >= 0 && expression.charAt(index) == '-') {
            if (index == 0 || isOperatorChar(expression.charAt(index - 1))) index--;
        }
        return index + 1;
    }

    private String currentEntry() {
        if (expression.length() == 0) return "0";
        if (endsWithOperator()) return "0";
        return expression.substring(currentNumberStart());
    }

    private void render() {
        if (!showingError && !justEvaluated) {
            expressionDisplay.setText(expression.toString().replace('*', '×').replace('/', '÷').replace('-', '−'));
            shownResult = currentEntry();
        }
        resultDisplay.setText(shownResult);
        resultDisplay.setTextColor(showingError ? 0xFFFF7B7B : 0xFFFFFFFF);
        resultDisplay.setContentDescription(showingError ? "Error: " + shownResult : "Result " + shownResult);
    }

    private void showError(String message) {
        showingError = true;
        justEvaluated = false;
        shownResult = "Error";
        expressionDisplay.setText(message);
    }

    private static boolean isDigit(String value) {
        return value.length() == 1 && value.charAt(0) >= '0' && value.charAt(0) <= '9';
    }

    private static boolean isOperator(String value) {
        return "+".equals(value) || "−".equals(value) || "×".equals(value) || "÷".equals(value);
    }

    private static boolean isOperatorChar(char value) {
        return value == '+' || value == '-' || value == '*' || value == '/';
    }

    private static char toAsciiOperator(String value) {
        switch (value) {
            case "×": return '*';
            case "÷": return '/';
            case "−": return '-';
            default: return '+';
        }
    }
}
