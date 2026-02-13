package io.github.c1921.comfyui_assistant.data.decoder.coil

import coil.ImageLoader
import coil.decode.BitmapFactoryDecoder
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import io.github.c1921.comfyui_assistant.data.decoder.DuckDecodeOutcome
import io.github.c1921.comfyui_assistant.data.decoder.DuckPayloadDecoder
import okio.Buffer

class DuckAutoDecodeDecoder(
    private val result: SourceResult,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val fallbackFactory: BitmapFactoryDecoder.Factory,
    private val payloadDecoder: DuckPayloadDecoder,
) : Decoder {
    override suspend fun decode(): DecodeResult? {
        val originalBytes = result.source.source().use { source ->
            source.readByteArray()
        }
        val outcome = payloadDecoder.decodeIfCarrierImage(
            imageBytes = originalBytes,
            password = DuckDecodeRequestParams.decodePassword(options),
        )
        val bytesToDecode = when (outcome) {
            is DuckDecodeOutcome.Decoded -> outcome.imageBytes
            is DuckDecodeOutcome.Fallback -> originalBytes
        }

        val remappedResult = result.copy(
            source = ImageSource(
                source = Buffer().write(bytesToDecode),
                context = options.context,
            )
        )
        return fallbackFactory.create(remappedResult, options, imageLoader).decode()
    }

    class Factory(
        private val fallbackFactory: BitmapFactoryDecoder.Factory = BitmapFactoryDecoder.Factory(),
        private val payloadDecoder: DuckPayloadDecoder = DuckPayloadDecoder(),
    ) : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!DuckDecodeRequestParams.isAutoDecodeEnabled(options)) {
                return null
            }
            return DuckAutoDecodeDecoder(
                result = result,
                options = options,
                imageLoader = imageLoader,
                fallbackFactory = fallbackFactory,
                payloadDecoder = payloadDecoder,
            )
        }
    }
}
