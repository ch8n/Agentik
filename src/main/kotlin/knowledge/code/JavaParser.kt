package knowledge.code

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.lang.java.JavaLanguage
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.psi.PsiClass
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.config.CompilerConfiguration

data class JavaClassBreakDown(
    val className: String,
    val classFields: List<JavaProperty>,
    val classMethods: List<JavaMethodBreakDown>,
    val entireClassBody: String,
    val classType: String // e.g., Object, Abstract, Interface, Enum
)

data class JavaProperty(
    val visibility: String,
    val valOrVar: String,  // "final", "static" or ""
    val propertyName: String,
    val propertyType: String
)

data class JavaMethodBreakDown(
    val methodName: String,
    val returnType: String,
    val parameters: List<String>,
    val visibility: String,
    val isStatic: Boolean,
    val isFinal: Boolean,
    val entireMethodBody: String
)

data class JavaFileBreakdown(
    val classBreakdowns: List<JavaClassBreakDown>,
    val entireFileCode: String
) : CodeBreakDown


fun extractJavaFields(psiClass: PsiClass): List<JavaProperty> {
    return psiClass.fields.map { field ->
        JavaProperty(
            visibility = when {
                field.hasModifierProperty(PsiModifier.PRIVATE) -> "private"
                field.hasModifierProperty(PsiModifier.PROTECTED) -> "protected"
                field.hasModifierProperty(PsiModifier.PUBLIC) -> "public"
                else -> "default"
            },
            valOrVar = when {
                field.hasModifierProperty(PsiModifier.FINAL) -> "final"
                field.hasModifierProperty(PsiModifier.STATIC) -> "static"
                else -> ""
            },
            propertyName = field.name,
            propertyType = field.type.presentableText
        )
    }
}

fun extractJavaMethods(psiClass: PsiClass): List<JavaMethodBreakDown> {
    return psiClass.methods.map { method ->
        JavaMethodBreakDown(
            methodName = method.name,
            returnType = method.returnType?.presentableText ?: "void",
            parameters = method.parameterList.parameters.map { "${it.name}: ${it.type.presentableText}" },
            visibility = when {
                method.hasModifierProperty(PsiModifier.PRIVATE) -> "private"
                method.hasModifierProperty(PsiModifier.PROTECTED) -> "protected"
                method.hasModifierProperty(PsiModifier.PUBLIC) -> "public"
                else -> "default"
            },
            isStatic = method.hasModifierProperty(PsiModifier.STATIC),
            isFinal = method.hasModifierProperty(PsiModifier.FINAL),
            entireMethodBody = method.text
        )
    }
}


fun extractJavaClassInfo(psiClass: PsiClass): JavaClassBreakDown {
    return JavaClassBreakDown(
        className = psiClass.name ?: "UnknownClass",
        classFields = extractJavaFields(psiClass),
        classMethods = extractJavaMethods(psiClass),
        entireClassBody = psiClass.text, // Captures entire declaration
        classType = when {
            psiClass.isInterface -> "Interface"
            psiClass.isEnum -> "Enum"
            psiClass.hasModifierProperty(PsiModifier.ABSTRACT) -> "Abstract Class"
            else -> "Class"
        }
    )
}

fun parseJavaCode(sourceCode: String): JavaFileBreakdown {
    val configuration = CompilerConfiguration()
    val kotlinEnv = KotlinCoreEnvironment.createForProduction(Disposable {}, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
    val psiFactory = PsiFileFactory.getInstance(kotlinEnv.project)
    val javaFile = psiFactory.createFileFromText("temp.java", JavaLanguage.INSTANCE, sourceCode) as PsiJavaFile

    val classBreakdowns = javaFile.classes.map { extractJavaClassInfo(it) }

    return JavaFileBreakdown(
        classBreakdowns = classBreakdowns,
        entireFileCode = sourceCode
    )
}

fun main() {
    val javaCode = """
        package example;

        public class Person {
            private String name;
            protected int age;
            public static final String SPECIES = "Human";

            public Person(String name, int age) {
                this.name = name;
                this.age = age;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

        abstract class Animal {
            public abstract void makeSound();
        }

        interface Loggable {
            void log(String message);
        }
    """.trimIndent()

    val breakdown = parseJavaCode(javaCode)

    // Print parsed output
    println("🔹 **Classes Detected:**")
    breakdown.classBreakdowns.forEach { classInfo ->
        println("\n🔹 ${classInfo.className} (${classInfo.classType})")
        println("📝 Full Declaration:\n${classInfo.entireClassBody}\n")

        println("  🔑 **Properties**:")
        classInfo.classFields.forEach {
            println("   - ${it.visibility} ${it.valOrVar} ${it.propertyName}: ${it.propertyType}")
        }

        println("  🔥 **Methods**:")
        classInfo.classMethods.forEach {
            println("   - ${it.visibility} Fun ${it.methodName}(${it.parameters.joinToString(", ")}): ${it.returnType}")
        }
    }
}