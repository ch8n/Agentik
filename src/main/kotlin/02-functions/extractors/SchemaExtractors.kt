package `02-functions`.extractors

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlin.reflect.KClass

/**
 * Sealed class representing the schema type of a serializable class's properties.
 */
sealed class SchemaType {
    /** Represents a String type. */
    object StringType : SchemaType()

    /** Represents an Int type. */
    object IntType : SchemaType()

    /** Represents a Boolean type. */
    object BooleanType : SchemaType()

    /** Represents a Double type. */
    object DoubleType : SchemaType()

    /** Represents a List type with its element type. */
    data class ListType(val elementType: SchemaType) : SchemaType()

    /** Represents an object (class) type with its properties as a map of name to type. */
    data class ObjectType(val properties: Map<String, SchemaType>) : SchemaType()

    /**
     * Provides a human-readable string representation of the schema type.
     */
    override fun toString(): String {
        return when (this) {
            is StringType -> "String"
            is IntType -> "Int"
            is BooleanType -> "Boolean"
            is DoubleType -> "Double"
            is ListType -> "List<${elementType}>"
            is ObjectType -> "Object { ${properties.map { "${it.key}: ${it.value}" }.joinToString(", ")} }"
        }
    }
}

/**
 * Converts a SerialDescriptor to a SchemaType by analyzing its structure.
 * @param descriptor The SerialDescriptor to convert.
 * @return The corresponding SchemaType.
 * @throws IllegalArgumentException if the descriptor kind is unsupported.
 */
fun descriptorToSchema(descriptor: SerialDescriptor): SchemaType {
    return when (descriptor.kind) {
        PrimitiveKind.STRING -> SchemaType.StringType
        PrimitiveKind.INT -> SchemaType.IntType
        PrimitiveKind.BOOLEAN -> SchemaType.BooleanType
        PrimitiveKind.DOUBLE -> SchemaType.DoubleType
        StructureKind.CLASS -> {
            val properties = (0 until descriptor.elementsCount).associate { i ->
                val propName = descriptor.getElementName(i)
                val propDescriptor = descriptor.getElementDescriptor(i)
                propName to descriptorToSchema(propDescriptor)
            }
            SchemaType.ObjectType(properties)
        }
        StructureKind.LIST -> {
            val elementDescriptor = descriptor.getElementDescriptor(0)
            SchemaType.ListType(descriptorToSchema(elementDescriptor))
        }
        else -> throw IllegalArgumentException("Unsupported descriptor kind: ${descriptor.kind}")
    }
}

/**
 * Extracts the schema of a given class using its serializer's descriptor.
 * @param kClass The KClass of the class whose schema is to be extracted.
 * @return The SchemaType representing the class's structure.
 * @throws IllegalArgumentException if the class is not serializable.
 */
@OptIn(InternalSerializationApi::class)
fun extractSchema(kClass: KClass<*>): SchemaType {
    val serializer = try {
        kClass.serializer()
    } catch (e: SerializationException) {
        throw IllegalArgumentException("Class is not serializable: ${kClass.simpleName}", e)
    }
    val descriptor = serializer.descriptor
    return descriptorToSchema(descriptor)
}

// Example usage with serializable data classes
@Serializable
data class User(
    val name: String,
    val age: Int,
    val isActive: Boolean
)

@Serializable
data class Department(
    val name: String,
    val employees: List<User>
)

/**
 * Main function to demonstrate schema extraction.
 */
fun main() {
    // Extract and print schema for User class
    val userSchema = extractSchema(User::class)
    println("User Schema: $userSchema")

    // Extract and print schema for Department class
    val deptSchema = extractSchema(Department::class)
    println("Department Schema: $deptSchema")
}