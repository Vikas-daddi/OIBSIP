package com.oasisinfobyte.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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
    private static final String STATE_SCIENTIFIC_OPEN = "calculator.scientificOpen";
    private static final String PREFS_NAME = "oasis_calculator";
    private static final String PREF_MEMORY = "memory.value";
    private static final String PREF_HAS_MEMORY = "memory.hasValue";
    private static final String PREF_HISTORY = "calculation.history";
    private static final String PREF_THEME_MODE = "theme.mode";
    private static final int THEME_FOLLOW_SYSTEM = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    private static final int MAX_HISTORY_ITEMS = 30;

    private TextView expressionDisplay;
    private TextView resultDisplay;
    private TextView memoryIndicator;
    private AlertDialog historyDialog;
    private Button themeButton;
    private LinearLayout scientificPanel;
    private StringBuilder expression = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private SharedPreferences preferences;
    private String shownResult = "0";
    private double memoryValue;
    private boolean hasMemoryValue;
    private boolean justEvaluated;
    private boolean showingError;
    private boolean darkTheme;
    private SelfUpdateManager selfUpdateManager;

    @Override
    protected void attachBaseContext(Context base) {
        int requestedTheme = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(PREF_THEME_MODE, THEME_FOLLOW_SYSTEM);
        if (requestedTheme != THEME_FOLLOW_SYSTEM) {
            Configuration configuration = new Configuration(base.getResources().getConfiguration());
            int nightMode = requestedTheme == THEME_DARK
                    ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
            super.attachBaseContext(base.createConfigurationContext(configuration));
        } else {
            super.attachBaseContext(base);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        expressionDisplay = findViewById(R.id.expressionDisplay);
        resultDisplay = findViewById(R.id.resultDisplay);
        memoryIndicator = findViewById(R.id.memoryIndicator);
        themeButton = findViewById(R.id.themeButton);
        scientificPanel = findViewById(R.id.scientificPanel);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadSavedData();
        darkTheme = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        selfUpdateManager = new SelfUpdateManager(this);

        bindButtons((ViewGroup) findViewById(android.R.id.content));
        updateThemeButton();
        findViewById(R.id.scientificToggle).setOnClickListener(view -> toggleScientificPanel());
        themeButton.setOnLongClickListener(view -> {
            preferences.edit().remove(PREF_THEME_MODE).apply();
            Toast.makeText(this, "Following system theme", Toast.LENGTH_SHORT).show();
            recreate();
            return true;
        });
        resultDisplay.setOnLongClickListener(view -> {
            copyResult();
            return true;
        });

        if (savedInstanceState != null) {
            expression = new StringBuilder(savedInstanceState.getString(STATE_EXPRESSION, ""));
            shownResult = savedInstanceState.getString(STATE_RESULT, "0");
            justEvaluated = savedInstanceState.getBoolean(STATE_JUST_EVALUATED, false);
            scientificPanel.setVisibility(savedInstanceState.getBoolean(STATE_SCIENTIFIC_OPEN, false)
                    ? View.VISIBLE : View.GONE);
            updateScientificToggle();
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
        outState.putBoolean(STATE_SCIENTIFIC_OPEN, scientificPanel.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View view) {
        if (!(view instanceof Button)) return;
        if (view.getId() == R.id.themeButton) {
            toggleTheme();
            return;
        }
        if (view.getId() == R.id.historyButton) {
            showHistory();
            return;
        }
        String key = ((Button) view).getText().toString();

        if ("C".equals(key) || "AC".equals(key)) {
            clear();
        } else if ("DEL".equals(key) || "⌫".equals(key)) {
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
        } else if ("()".equals(key)) {
            appendParenthesis();
        } else if ("sin".equals(key) || "cos".equals(key) || "tan".equals(key)
                || "ln".equals(key) || "log".equals(key)) {
            applyScientificFunction(key);
        } else if ("(".equals(key) || ")".equals(key)) {
            appendParenthesis(key);
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
        if (expression.length() > 0 && expression.charAt(expression.length() - 1) == ')') {
            expression.append('*').append(digit);
        } else if (endsWithOperator()) {
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
        if (expression.length() > 0 && expression.charAt(expression.length() - 1) == ')') {
            expression.append("*0.");
        } else if (endsWithOperator() || expression.length() == 0) {
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
        if (last == '(') {
            if ("−".equals(operator)) expression.append('-');
            return;
        }
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
        if (expression.length() > 0 && expression.charAt(expression.length() - 1) == ')') {
            try {
                double value = CalculatorExpression.evaluate(expression.toString()) / 100d;
                expression.setLength(0);
                expression.append(CalculatorExpression.format(value));
                shownResult = expression.toString();
            } catch (IllegalArgumentException | ArithmeticException exception) {
                showError("Invalid expression");
            }
            return;
        }
        if (expression.length() == 0 || endsWithOperator()) return;
        if (expression.charAt(expression.length() - 1) == ')') {
            try {
                double value = CalculatorExpression.evaluate(expression.toString());
                expression = new StringBuilder("-(" + expression + ")");
                shownResult = CalculatorExpression.format(-value);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                showError("Invalid expression");
            }
            return;
        }
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
        String valueBefore;
        try {
            valueBefore = justEvaluated ? shownResult : currentEntry();
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
                replaceCurrentValue(shownResult);
            }
        } catch (IllegalArgumentException exception) {
            showError("Result is outside the supported range");
        }
    }

    private void applyScientificFunction(String function) {
        if ("Copy".equals(function)) {
            copyResult();
            return;
        }
        if (showingError) clear();
        String valueBefore;
        try {
            valueBefore = justEvaluated ? shownResult : currentEntry();
            double value = Double.parseDouble(valueBefore);
            double result;
            switch (function) {
                case "sin": result = Math.sin(Math.toRadians(value)); break;
                case "cos": result = Math.cos(Math.toRadians(value)); break;
                case "tan": result = Math.tan(Math.toRadians(value)); break;
                case "ln":
                    if (value <= 0d) throw new IllegalArgumentException("Logarithm needs a positive number");
                    result = Math.log(value);
                    break;
                case "log":
                    if (value <= 0d) throw new IllegalArgumentException("Logarithm needs a positive number");
                    result = Math.log10(value);
                    break;
                default: return;
            }
            if (!Double.isFinite(result)) throw new IllegalArgumentException("Result is outside the supported range");
            shownResult = CalculatorExpression.format(result);
            if (justEvaluated) {
                expressionDisplay.setText(function + "(" + valueBefore + (isTrigonometric(function) ? "°)" : ")"));
            } else {
                replaceCurrentValue(shownResult);
            }
        } catch (NumberFormatException | ArithmeticException exception) {
            showError("Invalid scientific operation");
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage() == null ? "Invalid scientific operation" : exception.getMessage());
        }
    }

    private void replaceCurrentValue(String value) {
        if (expression.length() > 0 && expression.charAt(expression.length() - 1) == ')') {
            expression.setLength(0);
            expression.append(value);
            return;
        }
        int start = currentNumberStart();
        expression.replace(start, expression.length(), value);
    }

    private void appendParenthesis() {
        beginFreshEntryIfNeeded();
        if (expression.length() >= 128) return;
        int open = 0;
        int close = 0;
        for (int index = 0; index < expression.length(); index++) {
            if (expression.charAt(index) == '(') open++;
            else if (expression.charAt(index) == ')') close++;
        }
        char last = expression.length() == 0 ? '\0' : expression.charAt(expression.length() - 1);
        boolean canClose = open > close && ((last >= '0' && last <= '9') || last == ')' || last == '.');
        if (canClose) {
            expression.append(')');
        } else {
            if ((last >= '0' && last <= '9') || last == ')' || last == '.') expression.append('*');
            expression.append('(');
        }
        shownResult = currentEntry();
    }

    private void appendParenthesis(String bracket) {
        if ("(".equals(bracket)) {
            beginFreshEntryIfNeeded();
            if (expression.length() > 0) {
                char last = expression.charAt(expression.length() - 1);
                if ((last >= '0' && last <= '9') || last == ')') expression.append('*');
            }
            expression.append('(');
        } else if (expression.length() > 0 && !endsWithOperator()) {
            expression.append(')');
        }
        shownResult = currentEntry();
    }

    private void toggleScientificPanel() {
        scientificPanel.setVisibility(scientificPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        updateScientificToggle();
    }

    private void updateScientificToggle() {
        Button toggle = findViewById(R.id.scientificToggle);
        boolean expanded = scientificPanel.getVisibility() == View.VISIBLE;
        toggle.setText((expanded ? "⌃  " : "⌄  ") + "Scientific");
        toggle.setContentDescription(expanded ? "Hide scientific calculator functions" : "Show scientific calculator functions");
    }

    private void toggleTheme() {
        preferences.edit().putInt(PREF_THEME_MODE, darkTheme ? THEME_LIGHT : THEME_DARK).apply();
        recreate();
    }

    private void updateThemeButton() {
        themeButton.setText(darkTheme ? "☀" : "☾");
        themeButton.setContentDescription(darkTheme ? "Switch to light theme" : "Switch to dark theme");
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
            empty.setTextColor(getColor(R.color.hint_text));
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28));
            rows.addView(empty);
        } else {
            for (String item : history) {
                TextView row = new TextView(this);
                row.setText(item);
                row.setTextColor(getColor(R.color.dialog_text));
                row.setTextSize(17);
                row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                row.setPadding(dp(12), dp(14), dp(12), dp(14));
                row.setBackgroundResource(R.drawable.key_background);
                row.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.dialog_row_bg)));
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
        if (expression.length() == 0) return false;
        char last = expression.charAt(expression.length() - 1);
        return isOperatorChar(last) || last == '(';
    }

    private int currentNumberStart() {
        int index = expression.length() - 1;
        while (index >= 0) {
            char value = expression.charAt(index);
            if ((value >= '0' && value <= '9') || value == '.') index--;
            else break;
        }
        if (index >= 0 && expression.charAt(index) == '-') {
            if (index == 0 || isOperatorChar(expression.charAt(index - 1)) || expression.charAt(index - 1) == '(') index--;
        }
        return index + 1;
    }

    private String currentEntry() {
        if (expression.length() == 0) return "0";
        if (endsWithOperator()) return "0";
        if (expression.charAt(expression.length() - 1) == ')') {
            try {
                return CalculatorExpression.format(CalculatorExpression.evaluate(expression.toString()));
            } catch (IllegalArgumentException | ArithmeticException ignored) {
                return "0";
            }
        }
        return expression.substring(currentNumberStart());
    }

    private void render() {
        if (!showingError && !justEvaluated) {
            expressionDisplay.setText(expression.toString().replace('*', '×').replace('/', '÷').replace('-', '−'));
            String current = currentEntry();
            if (expression.length() > 0 && !endsWithOperator() && expression.charAt(expression.length() - 1) != '.') {
                try {
                    shownResult = CalculatorExpression.format(CalculatorExpression.evaluate(expression.toString()));
                } catch (IllegalArgumentException | ArithmeticException exception) {
                    shownResult = current;
                }
            } else {
                shownResult = current;
            }
        }
        resultDisplay.setText(shownResult);
        resultDisplay.setTextColor(getColor(showingError ? R.color.error_text : R.color.display_text));
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

    private static boolean isTrigonometric(String function) {
        return "sin".equals(function) || "cos".equals(function) || "tan".equals(function);
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
