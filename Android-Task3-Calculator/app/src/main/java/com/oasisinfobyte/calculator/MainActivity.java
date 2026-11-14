package com.oasisinfobyte.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Main screen and input controller for the calculator. */
public final class MainActivity extends Activity implements View.OnClickListener {
    private static final String STATE_EXPRESSION = "calculator.expression";
    private static final String STATE_RESULT = "calculator.result";
    private static final String STATE_JUST_EVALUATED = "calculator.justEvaluated";
    private static final String PREFS_NAME = "oasis_calculator";
    private static final String PREF_MEMORY = "memory.value";
    private static final String PREF_HAS_MEMORY = "memory.hasValue";
    private static final String PREF_HISTORY = "calculation.history";
    private static final int MAX_HISTORY_ITEMS = 30;

    private TextView expressionDisplay;
    private TextView resultDisplay;
    private TextView memoryIndicator;
    private AlertDialog historyDialog;
    private StringBuilder expression = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private SharedPreferences preferences;
    private String shownResult = "0";
    private double memoryValue;
    private boolean hasMemoryValue;
    private boolean justEvaluated;
    private boolean showingError;
    private SelfUpdateManager selfUpdateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        expressionDisplay = findViewById(R.id.expressionDisplay);
        resultDisplay = findViewById(R.id.resultDisplay);
        memoryIndicator = findViewById(R.id.memoryIndicator);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadSavedData();
        selfUpdateManager = new SelfUpdateManager(this);

        bindButtons((ViewGroup) findViewById(android.R.id.content));
        resultDisplay.setOnLongClickListener(view -> {
            copyResult();
            return true;
        });

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
        } else if ("√x".equals(key)) {
            applyUnary(false);
        } else if ("x²".equals(key)) {
            applyUnary(true);
        } else if ("◷".equals(key)) {
            showHistory();
        } else if ("Copy".equals(key)) {
            copyResult();
        } else if ("MC".equals(key)) {
            clearMemory();
        } else if ("MR".equals(key)) {
            recallMemory();
        } else if ("M+".equals(key)) {
            updateMemory(1);
        } else if ("M−".equals(key)) {
            updateMemory(-1);
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
        expressionDisplay.setText("");
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
            addHistory(completedExpression, shownResult);
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

    private void applyUnary(boolean square) {
        if (showingError) clear();
        String valueBefore = shownResult;
        try {
            double value = Double.parseDouble(valueBefore);
            if (!square && value < 0d) {
                showError("Square root needs a positive number");
                return;
            }
            double result = square ? value * value : Math.sqrt(value);
            shownResult = CalculatorExpression.format(result);
            if (justEvaluated) {
                expressionDisplay.setText((square ? valueBefore + "²" : "√(" + valueBefore + ")"));
            } else {
                int start = currentNumberStart();
                expression.replace(start, expression.length(), shownResult);
            }
        } catch (IllegalArgumentException exception) {
            showError("Result is outside the supported range");
        }
    }

    private void loadSavedData() {
        memoryValue = Double.longBitsToDouble(preferences.getLong(PREF_MEMORY, Double.doubleToLongBits(0d)));
        hasMemoryValue = preferences.getBoolean(PREF_HAS_MEMORY, false);
        JSONArray savedHistory;
        try {
            savedHistory = new JSONArray(preferences.getString(PREF_HISTORY, "[]"));
            for (int index = 0; index < savedHistory.length() && index < MAX_HISTORY_ITEMS; index++) {
                history.add(savedHistory.getString(index));
            }
        } catch (Exception ignored) {
            history.clear();
        }
    }

    private void bindButtons(ViewGroup group) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof Button) {
                child.setOnClickListener(this);
            } else if (child instanceof ViewGroup) {
                bindButtons((ViewGroup) child);
            }
        }
    }

    private void addHistory(String completedExpression, String result) {
        history.add(0, completedExpression + "  =  " + result);
        while (history.size() > MAX_HISTORY_ITEMS) history.remove(history.size() - 1);
        JSONArray saved = new JSONArray();
        for (String item : history) saved.put(item);
        preferences.edit().putString(PREF_HISTORY, saved.toString()).apply();
    }

    private void showHistory() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(18), dp(4), dp(18), dp(4));
        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Your calculations will appear here.");
            empty.setTextColor(0xFF777D91);
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28));
            rows.addView(empty);
        } else {
            for (String item : history) {
                TextView row = new TextView(this);
                row.setText(item);
                row.setTextColor(0xFF30364B);
                row.setTextSize(17);
                row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                row.setPadding(dp(12), dp(14), dp(12), dp(14));
                row.setBackgroundResource(R.drawable.key_background);
                row.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF2F3F9));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = dp(8);
                rows.addView(row, params);
                row.setOnClickListener(view -> {
                    recallHistory(item);
                    if (historyDialog != null) historyDialog.dismiss();
                });
            }
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(rows);
        historyDialog = new AlertDialog.Builder(this)
                .setTitle("Recent calculations")
                .setView(scrollView)
                .setNeutralButton("Clear history", (which, button) -> clearHistory())
                .setPositiveButton("Done", null)
                .create();
        historyDialog.show();
    }

    private void recallHistory(String item) {
        int separator = item.lastIndexOf("  =  ");
        if (separator < 0) return;
        String previousExpression = item.substring(0, separator);
        shownResult = item.substring(separator + 5);
        expression.setLength(0);
        justEvaluated = true;
        showingError = false;
        expressionDisplay.setText(previousExpression.replace('*', '×').replace('/', '÷').replace('-', '−'));
        render();
    }

    private void clearHistory() {
        history.clear();
        preferences.edit().remove(PREF_HISTORY).apply();
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
    }

    private void updateMemory(int direction) {
        if (showingError) return;
        try {
            double current = Double.parseDouble(shownResult);
            double updatedMemory = hasMemoryValue ? memoryValue + direction * current : direction * current;
            if (!Double.isFinite(updatedMemory)) {
                Toast.makeText(this, "Memory limit reached", Toast.LENGTH_SHORT).show();
                return;
            }
            memoryValue = updatedMemory;
            hasMemoryValue = true;
            saveMemory();
            Toast.makeText(this, direction > 0 ? "Added to memory" : "Subtracted from memory", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException exception) {
            Toast.makeText(this, "There is no number to store", Toast.LENGTH_SHORT).show();
        }
    }

    private void recallMemory() {
        if (!hasMemoryValue) {
            Toast.makeText(this, "Memory is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        expression.setLength(0);
        expression.append(CalculatorExpression.format(memoryValue));
        shownResult = currentEntry();
        justEvaluated = false;
        showingError = false;
        Toast.makeText(this, "Memory recalled", Toast.LENGTH_SHORT).show();
    }

    private void clearMemory() {
        memoryValue = 0d;
        hasMemoryValue = false;
        saveMemory();
        Toast.makeText(this, "Memory cleared", Toast.LENGTH_SHORT).show();
    }

    private void saveMemory() {
        preferences.edit()
                .putLong(PREF_MEMORY, Double.doubleToRawLongBits(memoryValue))
                .putBoolean(PREF_HAS_MEMORY, hasMemoryValue)
                .apply();
    }

    private void copyResult() {
        if (showingError) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Calculator result", shownResult));
        Toast.makeText(this, "Result copied", Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        resultDisplay.setTextColor(showingError ? 0xFFCE4D61 : 0xFF20243A);
        resultDisplay.setContentDescription(showingError ? "Error: " + shownResult : "Result " + shownResult);
        memoryIndicator.setVisibility(hasMemoryValue ? View.VISIBLE : View.GONE);
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
