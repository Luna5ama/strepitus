package dev.luna5ama.strepitus.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal

fun camelCaseToWords(input: String): String {
    return buildString {
        input.forEachIndexed { index, c ->
            if (c == '_' && index != 0) {
                append(' ')
                return@forEachIndexed
            }

            if (c.isUpperCase() && index != 0) {
                append(' ')
            }
            if (index == 0) {
                append(c.uppercase())
            } else {
                append(c.lowercase())
            }
        }
    }
}

fun camelCaseToTitle(input: String): String {
    return buildString {
        input.forEachIndexed { index, c ->
            if (c == '_' && index != 0) {
                append(' ')
                return@forEachIndexed
            }

            if (c.isUpperCase() && index != 0) {
                append(' ')
            }
            if (index == 0) {
                append(c.uppercase())
            } else {
                append(c)
            }
        }
    }
}

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        return BigDecimal(decoder.decodeString())
    }
}