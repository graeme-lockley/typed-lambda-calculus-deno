package tlca

import kotlin.test.Test
import kotlin.test.assertEquals

class InterpreterTest {
    @Test
    fun executeApp() {
        assertExecute("(\\a -> \\b -> a + b) 10 20", "30: Int")
    }

    @Test
    fun executeIf() {
        assertExecute("if (True) 1 else 2", "1: Int")
        assertExecute("if (False) 1 else 2", "2: Int")
    }

    @Test
    fun executeLam() {
        assertExecute("\\a -> \\b -> a + b", "function: Int -> Int -> Int")
    }

    @Test
    fun executeLet() {
        assertExecute(
            "let add a b = a + b and incr = add 1 ; incr 10",
            listOf(
                NestedString.Sequence(
                    listOf(
                        NestedString.Item("add = function: Int -> Int -> Int"),
                        NestedString.Item("incr = function: Int -> Int")
                    )
                ), NestedString.Item("11: Int")
            )
        )
    }

    @Test
    fun executeLetRec() {
        assertExecute(
            "let rec fact n = if (n == 0) 1 else n * (fact (n - 1)) ; fact",
            listOf(
                NestedString.Sequence(
                    listOf(
                        NestedString.Item("fact = function: Int -> Int")
                    )
                ),
                NestedString.Item("function: Int -> Int")
            )
        )
        assertExecute(
            "let rec fact n = if (n == 0) 1 else n * (fact (n - 1)) ; fact 5",
            listOf(
                NestedString.Sequence(
                    listOf(
                        NestedString.Item("fact = function: Int -> Int")
                    )
                ),
                NestedString.Item("120: Int")
            )
        )

        assertExecute(
            "let rec isOdd n = if (n == 0) False else isEven (n - 1) and isEven n = if (n == 0) True else isOdd (n - 1) ; isEven 5",
            listOf(
                NestedString.Sequence(
                    listOf(
                        NestedString.Item("isOdd = function: Int -> Bool"),
                        NestedString.Item("isEven = function: Int -> Bool")
                    )
                ),
                NestedString.Item("false: Bool")
            )
        )

        assertExecute(
            "let rec isOdd n = if (n == 0) False else isEven (n - 1) and isEven n = if (n == 0) True else isOdd (n - 1) ; isOdd 5",
            listOf(
                NestedString.Sequence(
                    listOf(
                        NestedString.Item("isOdd = function: Int -> Bool"),
                        NestedString.Item("isEven = function: Int -> Bool")
                    )
                ),
                NestedString.Item("true: Bool")
            )
        )
    }

    @Test
    fun executeLBool() {
        assertExecute("True", "true: Bool")
        assertExecute("False", "false: Bool")
    }

    @Test
    fun executeLInt() {
        assertExecute("123", "123: Int")
    }

    @Test
    fun executeOp() {
        assertExecute("1 == 2", "false: Bool")
        assertExecute("2 == 2", "true: Bool")

        assertExecute("3 + 2", "5: Int")
        assertExecute("3 - 2", "1: Int")
        assertExecute("3 * 2", "6: Int")
        assertExecute("9 / 2", "4: Int")
    }

    @Test
    fun executeVar() {
        assertExecute("let x = 1 ; x", listOf(NestedString.Item("x = 1: Int"), NestedString.Item("1: Int")))
        assertExecute("let x = True ; x", listOf(NestedString.Item("x = true: Bool"), NestedString.Item("true: Bool")))
        assertExecute("let x = \\a -> a ; x", listOf(NestedString.Item("x = function: V1 -> V1"), NestedString.Item("function: V2 -> V2")))
    }
}

private fun assertExecute(input: String, expected: List<NestedString>) {
    val ast = parse(input)
    val (values) = execute(ast)

    ast.forEachIndexed { index, expression ->
        val (value, type) = values[index]

        assertEquals(expected[index].toString(), expressionToNestedString(value, type, expression).toString())
    }
}

private fun assertExecute(input: String, expected: String) {
    assertExecute(input, listOf(NestedString.Item(expected)))
}
