package knowledge.code

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*


interface CodeBreakDown

object NoCodeBreakdown: CodeBreakDown

// Data classes to hold breakdown information
data class ClassBreakDown(
    val className: String,
    val classProperties: ArrayList<ClassProperty>,
    val classMethods: ArrayList<ClassMethodBreakDown>,
    val constructorParameters: List<ConstructorParameter>,
    val classType: String, // enum, data class, object, companion, interface etc
    val entireClassBody: String,
)

data class ClassProperty(
    val valOrVar: String,
    val propertyName: String,
    val propertyType: String,
    val entirePropertyBody: String
)

data class ClassMethodBreakDown(
    val methodName: String,
    val returnType: String,
    val parameters: List<String>,
    val entireMethodBody: String
)

data class ConstructorParameter(
    val parameterName: String,
    val parameterType: String,
    val valOrVar: String,
    val visibility: String
)

data class TopLevelFunction(
    val functionName: String,
    val returnType: String,
    val parameters: List<String>,
    val entireFunctionBody: String
)

data class TopLevelProperty(
    val propertyName: String,
    val propertyType: String,
    val valOrVar: String, // "val" or "var"
    val visibility: String,
    val entirePropertyBody: String
)

data class KotlinFileBreakdown(
    val topLevelFunctions: List<TopLevelFunction>,
    val topLevelProperties: List<TopLevelProperty>,
    val classBreakdowns: List<ClassBreakDown>,
    val entireFileCode: String
) : CodeBreakDown


fun extractTopLevelProperties(ktFile: KtFile): List<TopLevelProperty> {
    return ktFile.declarations.filterIsInstance<KtProperty>().map { property ->
        TopLevelProperty(
            propertyName = property.name ?: "Unknown",
            propertyType = property.typeReference?.text ?: "Unknown",
            valOrVar = if (property.isVar) "var" else "val",
            visibility = when {
                property.hasModifier(KtTokens.PRIVATE_KEYWORD) -> "private"
                property.hasModifier(KtTokens.PROTECTED_KEYWORD) -> "protected"
                property.hasModifier(KtTokens.INTERNAL_KEYWORD) -> "internal"
                else -> "public"
            },
            entirePropertyBody = property.text
        )
    }
}

fun extractTopLevelFunctions(ktFile: KtFile): List<TopLevelFunction> {
    return ktFile.declarations.filterIsInstance<KtNamedFunction>().map { function ->
        TopLevelFunction(
            functionName = function.name ?: "UnknownFunction",
            returnType = function.typeReference?.text ?: "Unit",
            parameters = function.valueParameters.map { param ->
                "${param.name}: ${param.typeReference?.text ?: "Unknown"}"
            },
            entireFunctionBody = function.text
        )
    }
}


fun extractClassConstructorParameters(klass: KtClass): List<ConstructorParameter> {
    return klass.primaryConstructor?.valueParameters?.map { param ->
        ConstructorParameter(
            parameterName = param.name ?: "Unknown",
            parameterType = param.typeReference?.text ?: "Unknown",
            valOrVar = param.valOrVarKeyword?.text ?: "", // Whether it's 'val' or 'var'
            visibility = when {
                param.hasModifier(KtTokens.PRIVATE_KEYWORD) -> "private"
                param.hasModifier(KtTokens.PROTECTED_KEYWORD) -> "protected"
                param.hasModifier(KtTokens.INTERNAL_KEYWORD) -> "internal"
                else -> "public"
            }
        )
    } ?: emptyList()
}

// Function to determine class type (data class, object, etc.)
fun determineClassType(klass: KtClassOrObject): String {
    return when {
        klass is KtClass && klass.hasModifier(KtTokens.ABSTRACT_KEYWORD) -> "Abstract Class"
        klass is KtClass && klass.hasModifier(KtTokens.VALUE_KEYWORD) ||
                klass.annotationEntries.any { it.text.contains("JvmInline") } -> "Value Class"

        klass is KtClass && klass.isData() -> "Data Class"
        klass is KtClass && klass.isEnum() -> "Enum"
        klass is KtClass && klass.isInterface() -> "Interface"
        klass is KtClass && klass.isSealed() -> "Sealed Class"
        klass is KtClass && klass.isInner() -> "Inner Class"
        klass is KtObjectDeclaration && klass.isCompanion() -> "Companion Object"
        klass is KtObjectDeclaration -> "Object"
        klass is KtClass -> "Class"
        else -> "Unknown"
    }
}

// Extract properties from a class
fun extractClassProperties(klass: KtClass): ArrayList<ClassProperty> {
    val properties = arrayListOf<ClassProperty>()
    klass.getProperties().forEach {
        val typeRef = it.typeReference?.text ?: "Unknown"
        properties.add(
            ClassProperty(
                valOrVar = if (it.isVar) "var" else "val",
                propertyName = it.name ?: "Unknown",
                propertyType = typeRef,
                entirePropertyBody = it.text
            )
        )
    }
    return properties
}

// Extract methods from a class
fun extractClassMethods(klass: KtClass): ArrayList<ClassMethodBreakDown> {
    val methods = arrayListOf<ClassMethodBreakDown>()
    klass.declarations.forEach {
        if (it is KtNamedFunction) {
            val methodName = it.name ?: "UnnamedMethod"
            val returnType = it.typeReference?.text ?: "Unit"
            val parameters =
                it.valueParameters.map { param -> "${param.name}: ${param.typeReference?.text ?: "Unknown"}" }
            val entireMethodBody = it.text

            methods.add(
                ClassMethodBreakDown(
                    methodName = methodName,
                    returnType = returnType,
                    parameters = parameters,
                    entireMethodBody = entireMethodBody
                )
            )
        }
    }
    return methods
}


// Function to parse Kotlin source code
fun parseKotlinCode(sourceCode: String): KotlinFileBreakdown {
    val configuration = CompilerConfiguration()
    val kotlinEnv = KotlinCoreEnvironment.createForProduction(Disposable {}, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
    val psiFactory = PsiFileFactory.getInstance(kotlinEnv.project)
    val ktFile = psiFactory.createFileFromText("temp.kt", KotlinLanguage.INSTANCE, sourceCode) as KtFile

    val classBreakdowns = mutableListOf<ClassBreakDown>()

    ktFile.declarations.forEach { declaration ->
        if (declaration is KtClass) {
            val className = declaration.name ?: "UnknownClass"
            val classType = determineClassType(declaration)
            val properties = extractClassProperties(declaration)
            val methods = extractClassMethods(declaration)
            val constructorParams = extractClassConstructorParameters(declaration)
            val classBreakDown = ClassBreakDown(
                className = className,
                classProperties = properties,
                classMethods = methods,
                entireClassBody = declaration.text,
                classType = classType,
                constructorParameters = constructorParams
            )
            classBreakdowns.add(classBreakDown)
        }
    }

    return KotlinFileBreakdown(
        topLevelFunctions = extractTopLevelFunctions(ktFile),
        topLevelProperties = extractTopLevelProperties(ktFile),
        classBreakdowns = classBreakdowns,
        entireFileCode = sourceCode
    )
}


fun main() {
    val kotlinCode = """
        // Top-level property
        val globalMessage: String = "Hello, World!"

        // Top-level function
        fun printMessage() {
            println(globalMessage)
        }

        fun addNumbers(a: Int, b: Int): Int {
            return a + b
        }

        // A data class
        data class Person(val name: String, val age: Int) {
            fun greet() = "Hello, I'm Chetan"
        }

        // A sealed class
        sealed class Result {
            data class Success(val data: String) : Result()
            data class Failure(val error: String) : Result()
        }

        // An object declaration
        object Singleton {
            fun log(message: String) {
                println(message)
            }
        }
    """.trimIndent()

    // Parse the Kotlin code
    val breakdown = parseKotlinCode(kotlinCode)

    // Print results
    println("\n===== 🏆 PARSED KOTLIN STRUCTURE 🏆 =====\n")

    println("🔹 **Top-Level Properties:**")
    breakdown.topLevelProperties.forEach {
        println(" - ${it.visibility} ${it.valOrVar} ${it.propertyName}: ${it.propertyType}")
    }
    println()

    println("🔹 **Top-Level Functions:**")
    breakdown.topLevelFunctions.forEach {
        println(" - Fun ${it.functionName}(${it.parameters.joinToString(", ")}): ${it.returnType}")
    }
    println()

    println("🔹 **Classes & Objects:**")
    breakdown.classBreakdowns.forEach { classInfo ->
        println("\n🔹 Class: **${classInfo.className}** (${classInfo.classType})")
        println("📝 Full Declaration:\n${classInfo.entireClassBody}\n")

        println("  ✨ **Constructor Parameters:**")
        classInfo.constructorParameters.forEach {
            println("   - ${it.visibility} ${it.valOrVar} ${it.parameterName}: ${it.parameterType}")
        }

        println("  🔑 **Properties:**")
        classInfo.classProperties.forEach {
            println("   - ${it.valOrVar} ${it.propertyName}: ${it.propertyType}")
        }

        println("  🔥 **Methods:**")
        classInfo.classMethods.forEach {
            println("   - Fun ${it.methodName}(${it.parameters.joinToString(", ")}): ${it.returnType}")
        }
    }

    println("\n===== ✅ PARSING COMPLETE ✅ =====\n")
}