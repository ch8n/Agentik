package data.ktx

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts a FloatArray to ByteArray.
 */
fun doubleArrayToByteArray(embedding: DoubleArray): ByteArray {
    val byteBuffer = ByteBuffer.allocate(embedding.size * 8)
    byteBuffer.order(ByteOrder.BIG_ENDIAN)
    for (double in embedding) {
        byteBuffer.putDouble(double)
    }
    return byteBuffer.array()
}

/**
 * Converts ByteArray to FloatArray.
 */
fun byteArrayToDoubleArray(bytes: ByteArray): DoubleArray {
    require(bytes.size % 8 == 0) { "Byte array size must be a multiple of 8." }

    val doubleArray = DoubleArray(bytes.size / 8)
    val byteBuffer = ByteBuffer.wrap(bytes)
    byteBuffer.order(ByteOrder.BIG_ENDIAN)
    for (i in doubleArray.indices) {
        doubleArray[i] = byteBuffer.double
    }
    return doubleArray
}
