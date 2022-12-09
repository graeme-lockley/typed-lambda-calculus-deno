package tlca

import io.littlelanguages.data.Tuple2
import tlca.parser.Parser
import tlca.parser.Scanner
import tlca.parser.Token
import tlca.parser.Visitor
import java.io.StringReader

sealed class Expression

data class AppExpression(val e1: Expression, val e2: Expression) : Expression()

data class IfExpression(val e1: Expression, val e2: Expression, val e3: Expression) : Expression()

data class LetExpression(val decls: List<Declaration>, val expr: Expression?) : Expression()

data class LetRecExpression(val decls: List<Declaration>, val expr: Expression?) : Expression()

data class Declaration(val n: String, val e: Expression)

data class LamExpression(val n: String, val e: Expression) : Expression()

data class LBoolExpression(val v: Boolean) : Expression()

data class LIntExpression(val v: Int) : Expression()

data class LStringExpression(val v: String) : Expression()

data class LTupleExpression(val es: List<Expression>) : Expression()

object LUnitExpression : Expression()

data class MatchExpression(val e: Expression, val cases: List<MatchCase>) : Expression()

data class MatchCase(val pattern: Pattern, val expr: Expression)

data class OpExpression(val e1: Expression, val e2: Expression, val op: Op) : Expression()

enum class Op { Equals, Plus, Minus, Times, Divide }

data class VarExpression(val name: String) : Expression()

sealed class Pattern

data class PBoolPattern(val v: Boolean) : Pattern()

data class PIntPattern(val v: Int) : Pattern()

data class PStringPattern(val v: String) : Pattern()

data class PTuplePattern(val values: List<Pattern>) : Pattern()

object PUnitPattern : Pattern()

data class PVarPattern(val name: String) : Pattern()

object PWildcardPattern : Pattern()

class ParserVisitor : Visitor<List<Expression>, Expression, Expression, Expression, Op, Expression, Op, Expression, Declaration, MatchCase, Pattern> {
    override fun visitProgram(a1: Expression, a2: List<Tuple2<Token, Expression>>): List<Expression> =
        listOf(a1) + a2.map { it.b }

    override fun visitExpression(a1: Expression, a2: List<Expression>): Expression = a2.fold(a1) { acc, e -> AppExpression(acc, e) }

    override fun visitRelational(a1: Expression, a2: Tuple2<Token, Expression>?): Expression =
        if (a2 == null) a1 else OpExpression(a1, a2.b, Op.Equals)

    override fun visitMultiplicative(a1: Expression, a2: List<Tuple2<Op, Expression>>): Expression =
        a2.fold(a1) { acc, e -> OpExpression(acc, e.b, e.a) }

    override fun visitMultiplicativeOps1(a: Token): Op = Op.Times

    override fun visitMultiplicativeOps2(a: Token): Op = Op.Divide

    override fun visitAdditive(a1: Expression, a2: List<Tuple2<Op, Expression>>): Expression = a2.fold(a1) { acc, e -> OpExpression(acc, e.b, e.a) }

    override fun visitAdditiveOps1(a: Token): Op = Op.Plus

    override fun visitAdditiveOps2(a: Token): Op = Op.Minus

    override fun visitFactor1(a1: Token, a2: Tuple2<Expression, List<Tuple2<Token, Expression>>>?, a3: Token): Expression =
        when {
            a2 == null -> LUnitExpression
            a2.b.isEmpty() -> a2.a
            else -> LTupleExpression(listOf(a2.a) + a2.b.map { it.b })
        }

    override fun visitFactor2(a: Token): Expression = LIntExpression(a.lexeme.toInt())

    override fun visitFactor3(a: Token): Expression = LStringExpression(a.lexeme.drop(1).dropLast(1).replace("\\\"", "\""))

    override fun visitFactor4(a: Token): Expression = LBoolExpression(true)

    override fun visitFactor5(a: Token): Expression = LBoolExpression(false)

    override fun visitFactor6(a1: Token, a2: Token, a3: List<Token>, a4: Token, a5: Expression): Expression =
        composeLambda(listOf(a2.lexeme) + a3.map { it.lexeme }, a5)

    override fun visitFactor7(
        a1: Token, a2: Token?, a3: Declaration, a4: List<Tuple2<Token, Declaration>>, a5: Tuple2<Token, Expression>?
    ): Expression {
        val declarations = listOf(a3) + a4.map { it.b }

        return if (a2 == null) LetExpression(declarations, a5?.b)
        else LetRecExpression(declarations, a5?.b)
    }

    override fun visitFactor8(a1: Token, a2: Token, a3: Expression, a4: Token, a5: Expression, a6: Token, a7: Expression): Expression =
        IfExpression(a3, a5, a7)


    override fun visitFactor9(a: Token): Expression = VarExpression(a.lexeme)

    override fun visitFactor10(a1: Token, a2: Expression, a3: Token, a4: Token?, a5: MatchCase, a6: List<Tuple2<Token, MatchCase>>): Expression =
        MatchExpression(a2, listOf(a5) + a6.map { it.b })

    override fun visitCase(a1: Pattern, a2: Token, a3: Expression): MatchCase = MatchCase(a1, a3)

    override fun visitPattern1(a1: Token, a2: Tuple2<Pattern, List<Tuple2<Token, Pattern>>>?, a3: Token): Pattern =
        when {
            a2 == null -> PUnitPattern
            a2.b.isEmpty() -> a2.a
            else -> PTuplePattern(listOf(a2.a) + a2.b.map { it.b })
        }

    override fun visitPattern2(a: Token): Pattern = PIntPattern(a.lexeme.toInt())

    override fun visitPattern3(a: Token): Pattern = PStringPattern(a.lexeme.drop(1).dropLast(1).replace("\\\"", "\""))

    override fun visitPattern4(a: Token): Pattern = PBoolPattern(true)

    override fun visitPattern5(a: Token): Pattern = PBoolPattern(false)

    override fun visitPattern6(a: Token): Pattern =
        if (a.lexeme == "_") PWildcardPattern else PVarPattern(a.lexeme)

    override fun visitDeclaration(a1: Token, a2: List<Token>, a3: Token, a4: Expression): Declaration =
        Declaration(a1.lexeme, composeLambda(a2.map { it.lexeme }, a4))

    private fun composeLambda(names: List<String>, e: Expression): Expression = names.foldRight(e) { name, acc -> LamExpression(name, acc) }
}

fun parse(scanner: Scanner): List<Expression> =
    Parser(scanner, ParserVisitor()).program()

fun parse(input: String): List<Expression> =
    parse(Scanner(StringReader(input)))

