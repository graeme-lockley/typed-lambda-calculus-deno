package tlca

data class Environment(val runtimeEnv: RuntimeEnv, val typeEnv: TypeEnv)

val emptyEnvironment = Environment(emptyMap(), emptyTypeEnv)

val defaultEnvironment = Environment(
    mapOf(
        "string_length" to { s: String -> s.length },
        "string_concat" to { s1: String -> { s2: String -> s1 + s2 } },
        "string_substring" to { s: String -> { start: Int -> { end: Int -> stringSubstring(s, start, end) } } },
        "string_equal" to { s1: String -> { s2: String -> s1 == s2 } },
        "string_compare" to { s1: String -> { s2: String -> if (s1 < s2) -1 else if (s1 == s2) 0 else 1 } },
    ), emptyTypeEnv
        .extend("string_length", Scheme(setOf(), TArr(typeString, typeInt)))
        .extend("string_concat", Scheme(setOf(), TArr(typeString, TArr(typeString, typeString))))
        .extend("string_substring", Scheme(setOf(), TArr(typeString, TArr(typeInt, TArr(typeInt, typeString)))))
        .extend("string_equal", Scheme(setOf(), TArr(typeString, TArr(typeString, typeBool))))
        .extend("string_compare", Scheme(setOf(), TArr(typeString, TArr(typeString, typeInt))))
)

fun stringSubstring(str: String, start: Int, end: Int): String {
    if (start > end) return ""
    val length = str.length
    val s = if (start < 0) 0 else if (start > length) length else start
    val e = if (end < 0) 0 else if (end > length) length else end
    return str.substring(s, e)
}

typealias Value = Any

typealias RuntimeEnv = Map<String, Value?>

data class TypedValue(val value: Value?, val type: Type)

data class ExecuteResult(val values: List<TypedValue>, val env: Environment)

fun execute(ast: List<Expression>, defaultEnv: Environment = emptyEnvironment): ExecuteResult {
    val pump = Pump()
    val values = mutableListOf<TypedValue>()
    var env = defaultEnv

    for (e in ast) {
        val inferResult = infer(env.typeEnv, e, Constraints(), pump)

        val subst = inferResult.constraints.solve()
        val type = inferResult.type.apply(subst)

        val evaluateResult: EvaluateResult = when (e) {
            is LetExpression -> evaluateDeclarations(e.decls, e.expr, env.runtimeEnv)
            is LetRecExpression -> evaluateDeclarations(e.decls, e.expr, env.runtimeEnv)
            else -> EvaluateResult(evaluate(e, env.runtimeEnv), env.runtimeEnv)
        }

        values.add(TypedValue(evaluateResult.value, type))

        env = Environment(evaluateResult.env, inferResult.typeEnv)
    }

    return ExecuteResult(values, env)
}

private val binaryOps: Map<Op, (Any?, Any?) -> Any> = mapOf(
    Pair(Op.Plus) { a: Any?, b: Any? -> (a as Int) + (b as Int) },
    Pair(Op.Minus) { a: Any?, b: Any? -> (a as Int) - (b as Int) },
    Pair(Op.Times) { a: Any?, b: Any? -> (a as Int) * (b as Int) },
    Pair(Op.Divide) { a: Any?, b: Any? -> (a as Int) / (b as Int) },
    Pair(Op.Equals) { a: Any?, b: Any? -> a == b }
)

private data class EvaluateResult(val value: Any?, val env: RuntimeEnv)

@Suppress("UNCHECKED_CAST")
private fun evaluate(ast: Expression, env: RuntimeEnv): Value? =
    when (ast) {
        is AppExpression -> {
            val function = evaluate(ast.e1, env) as (Any?) -> Any

            function(evaluate(ast.e2, env))
        }

        is IfExpression ->
            if (evaluate(ast.e1, env) as Boolean)
                evaluate(ast.e2, env)
            else
                evaluate(ast.e3, env)

        is LamExpression ->
            { x: Any -> evaluate(ast.e, env + Pair(ast.n, x)) }

        is LetExpression -> evaluateDeclarations(ast.decls, ast.expr, env).value
        is LetRecExpression -> evaluateDeclarations(ast.decls, ast.expr, env).value
        is LBoolExpression -> ast.v
        is LIntExpression -> ast.v
        is LStringExpression -> ast.v
        is LTupleExpression -> ast.es.map { evaluate(it, env) }
        LUnitExpression -> null
        is MatchExpression -> matchExpression(ast, env)
        is OpExpression -> binaryOps[ast.op]!!(evaluate(ast.e1, env), evaluate(ast.e2, env))
        is VarExpression -> env[ast.name]
    }

private fun matchExpression(e: MatchExpression, env: RuntimeEnv): Value? {
    val value = evaluate(e.e, env)

    for (c in e.cases) {
        val newEnv = matchPattern(c.pattern, value, env)
        if (newEnv != null) {
            return evaluate(c.expr, newEnv)
        }
    }

    throw Exception("No match")
}

private fun matchPattern(pattern: Pattern, value: Any?, env: RuntimeEnv): RuntimeEnv? =
    when (pattern) {
        is PBoolPattern -> if (pattern.v == value) env else null
        is PIntPattern -> if (pattern.v == value) env else null
        is PStringPattern -> if (pattern.v == value) env else null
        is PVarPattern -> env + Pair(pattern.name, value)
        is PTuplePattern -> {
            var newEnv: RuntimeEnv? = env
            for ((p, v) in pattern.values.zip(value as List<Any?>)) {
                if (newEnv != null) {
                    newEnv = matchPattern(p, v, newEnv)
                }
            }
            newEnv
        }
        PUnitPattern -> if (value == null) env else null
        PWildcardPattern -> env
    }

private fun evaluateDeclarations(decls: List<Declaration>, expr: Expression?, env: RuntimeEnv): EvaluateResult {
    val newEnv = env.toMutableMap()
    val values = mutableListOf<Value?>()

    for (decl in decls) {
        val value = evaluate(decl.e, newEnv)

        values.add(value)
        newEnv[decl.n] = value
    }

    return when (expr) {
        null -> EvaluateResult(values, newEnv)
        else -> EvaluateResult(evaluate(expr, newEnv), env)
    }
}

fun valueToString(value: Value?, type: Type): String =
    when (type) {
        typeUnit -> "()"
        typeString -> "\"${(value as String).replace("\"", "\\\"")}\""
        is TTuple -> {
            val values = value as List<Value?>
            val types = type.types

            values.zip(types).joinToString(", ", "(", ")") { (v, t) -> valueToString(v, t) }
        }

        is TArr -> "function"
        else -> value.toString()
    }

fun expressionToNestedString(value: Value?, type: Type, e: Expression): NestedString {
    fun declarationsToNestedString(decls: List<Declaration>, type: TTuple): NestedString =
        NestedString.Sequence(decls.mapIndexed { i, d ->
            NestedString.Item(
                "${d.n} = ${
                    valueToString(
                        (value as List<Value?>)[i],
                        type.types[i]
                    )
                }: ${type.types[i]}"
            )
        })

    return when {
        e is LetExpression && type is TTuple -> declarationsToNestedString(e.decls, type)
        e is LetRecExpression && type is TTuple -> declarationsToNestedString(e.decls, type)
        else -> NestedString.Item("${valueToString(value, type)}: $type")
    }
}

open class NestedString private constructor() {
    class Sequence(private val s: List<NestedString>) : NestedString() {
        override fun toString(): String = s.joinToString("\n")
    }

    class Item(private val v: String) : NestedString() {
        override fun toString(): String = v
    }
}
