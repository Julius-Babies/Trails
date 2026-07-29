package es.jvbabi.trails

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Serializable(with = OptionalSerializer::class)
sealed class Optional<T> {
    fun getOrNull(): T? = when (this) {
        is Defined -> value
        is Undefined -> null
    }

    fun getOrElse(default: T): T = getOrNull() ?: default

    data object Undefined : Optional<Nothing>() {
        @Suppress("UNCHECKED_CAST")
        operator fun <T> invoke(): Optional<T> = this as Optional<T>
    }
    data class Defined<T>(val value: T) : Optional<T>()
}

@OptIn(ExperimentalContracts::class)
fun <T> Optional<T>.isDefined(): Boolean {
    contract {
        returns(true) implies (this@isDefined is Optional.Defined<T>)
    }
    return this is Optional.Defined<T>
}

/**
 * Runs [block] with the value if this optional is defined, otherwise does nothing.
 *
 * Prefer this over `if (x.isDefined()) x.value` when `x` is a property of a DTO:
 * the [isDefined] contract cannot smart-cast a property that is declared in
 * another module, and unlike `getOrNull()` it keeps a defined-but-null value
 * distinguishable from an absent one.
 */
inline fun <T> Optional<T>.ifDefined(block: (T) -> Unit) {
    if (this is Optional.Defined) block(value)
}

class OptionalSerializer<T>(private val serializer: KSerializer<T>) : KSerializer<Optional<T>> {
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: Optional<T>) {
        when (value) {
            is Optional.Defined -> encoder.encodeSerializableValue(serializer, value.value)
            is Optional.Undefined -> {} // Do nothing if Undefined
        }
    }

    override fun deserialize(decoder: Decoder): Optional<T> {
        return Optional.Defined(decoder.decodeSerializableValue(serializer))
    }
}