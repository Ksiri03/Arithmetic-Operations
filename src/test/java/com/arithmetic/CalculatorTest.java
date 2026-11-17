package com.arithmetic;

import java.math.BigDecimal;

/**
 * Zero-dependency test runner. Runs with `java -cp build/classes com.arithmetic.CalculatorTest`
 * and exits with a non-zero status if any assertion fails - enough for Jenkins.
 */
public final class CalculatorTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        test("addition", () -> {
            var result = Calculator.calculate(
                    Calculator.parse("12"), Calculator.parse("8"), Calculator.Operation.ADD);
            assertEquals("20", Calculator.format(result.value()));
            assertEquals("12 + 8", result.expression());
        });

        test("subtraction with a negative result", () -> {
            var result = compute("4", "9", Calculator.Operation.SUBTRACT);
            assertEquals("-5", Calculator.format(result));
        });

        test("multiplication with decimals", () -> {
            var result = compute("2.5", "4", Calculator.Operation.MULTIPLY);
            assertEquals("10", Calculator.format(result));
        });

        test("division with a decimal result", () -> {
            var result = compute("12", "8", Calculator.Operation.DIVIDE);
            assertEquals("1.5", Calculator.format(result));
        });

        test("repeating division is rounded to ten places", () -> {
            var result = compute("1", "3", Calculator.Operation.DIVIDE);
            assertEquals("0.3333333333", Calculator.format(result));
        });

        test("negative operands", () -> {
            var result = compute("-12", "-3", Calculator.Operation.DIVIDE);
            assertEquals("4", Calculator.format(result));
        });

        test("large values stay exact", () -> {
            var result = compute("999999999999999999", "1", Calculator.Operation.ADD);
            assertEquals("1000000000000000000", Calculator.format(result));
        });

        test("division by zero is rejected", () -> {
            try {
                compute("10", "0", Calculator.Operation.DIVIDE);
                fail("expected division by zero to be rejected");
            } catch (ArithmeticException ex) {
                assertTrue(ex.getMessage().contains("Division by zero"));
            }
        });

        test("invalid number is rejected", () -> {
            try {
                Calculator.parse("twelve");
                fail("expected invalid input to be rejected");
            } catch (IllegalArgumentException ex) {
                assertTrue(ex.getMessage().contains("not a valid number"));
            }
        });

        test("empty input is rejected", () -> {
            try {
                Calculator.parse("  ");
                fail("expected empty input to be rejected");
            } catch (IllegalArgumentException ex) {
                assertTrue(ex.getMessage().contains("Enter both operands"));
            }
        });

        test("operation aliases work for the API", () -> {
            assertEquals("multiply", Calculator.Operation.from("*").id());
            assertEquals("divide", Calculator.Operation.from("/").id());
            assertEquals("subtract", Calculator.Operation.from("-").id());
            assertEquals("add", Calculator.Operation.from("+").id());
        });

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static BigDecimal compute(String a, String b, Calculator.Operation operation) {
        return Calculator.calculate(Calculator.parse(a), Calculator.parse(b), operation).value();
    }

    private static void test(String name, Runnable body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable error) {
            failed++;
            System.out.println("FAIL  " + name + " - " + error.getMessage());
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("expected condition to be true");
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
