package functions.maths

import agent.AgentikTool
import dev.langchain4j.agent.tool.Tool
import kotlin.math.*


class MathsKtx : AgentikTool {
    @Tool("Returns the absolute value of a number")
    fun absoluteValueOfNumber(number: Double): Double {
        return abs(number)
    }

    @Tool("Returns minimum from list of numbers")
    fun minimumOf(vararg number: Double): Double {
        println("called minimumOf ${number.joinToString()}")
        val take1 = number.first()
        val rest = number.drop(1)
        return minOf(take1, *rest.toTypedArray())
    }

    @Tool("Returns maximum from list of numbers")
    fun maximumOf(vararg number: Double): Double {
        val take1 = number.first()
        val rest = number.drop(1)
        return maxOf(take1, *rest.toTypedArray())
    }

    @Tool("Computes the square root of a number")
    fun squareRootOf(number: Double): Double = sqrt(number)

    @Tool("Raises a number to the power of another number")
    fun powerOf(number: Double, power: Double): Double = number.pow(power).also {
        print("called powerOf $number $power")
    }

    @Tool("Round a floating point number to nearest integer, example: 2.5 to 3.0")
    fun roundOff(number: Double) = round(number)

    @Tool("Floor a floating point number to down integer, example: 2.9 to 2.0")
    fun floorOf(number: Double) = floor(number)

    @Tool("Ceil a floating point number to nearest integer, example: 2.1 to 3.0")
    fun cielOf(number: Double) = ceil(number)

    @Tool("Returns sum from list of numbers")
    fun sumOf(vararg number: Double): Double = number.sum()

    @Tool("Returns minus from list of numbers")
    fun minusOf(vararg number: Double): Double = number.reduce { acc, it -> acc - it }

    @Tool("Returns product from list of numbers")
    fun productOf(vararg number: Double): Double = number.reduce { acc, it -> acc * it }

    @Tool("Returns division of two numbers")
    fun productOf(number1: Double, number2: Double): Double = number1.div(number2)
}


