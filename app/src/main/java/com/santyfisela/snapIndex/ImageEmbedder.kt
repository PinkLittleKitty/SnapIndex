package com.santyfisela.snapIndex

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageEmbedder(context: Context) {

    companion object {
        private const val MODEL_FILENAME = "openai_clip.tflite"
        private const val IMAGE_SIZE = 224
        private const val EMBEDDING_SIZE = 512
    }

    private val interpreter: Interpreter
    private val inputImageBuffer: TensorImage
    private val outputBuffer: TensorBuffer

    init {
        // Load TFLite model from assets folder
        val model = FileUtil.loadMappedFile(context, MODEL_FILENAME)
        interpreter = Interpreter(model)

        // Prepare input and output buffers
        inputImageBuffer = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
        outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, EMBEDDING_SIZE), org.tensorflow.lite.DataType.FLOAT32)
    }

    fun extractEmbedding(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        inputImageBuffer.load(resized)
        normalize(inputImageBuffer.buffer)
        interpreter.run(inputImageBuffer.buffer, outputBuffer.buffer.rewind())
        return outputBuffer.floatArray
    }

    private fun normalize(buffer: ByteBuffer) {
        buffer.rewind()
        val floatBuffer = buffer.asFloatBuffer()
        val size = floatBuffer.capacity()

        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

        // Iterate over each pixel (3 floats per pixel)
        for (i in 0 until size step 3) {
            floatBuffer.put(i, (floatBuffer.get(i) - mean[0]) / std[0])       // R
            floatBuffer.put(i + 1, (floatBuffer.get(i + 1) - mean[1]) / std[1]) // G
            floatBuffer.put(i + 2, (floatBuffer.get(i + 2) - mean[2]) / std[2]) // B
        }
    }

    fun close() {
        interpreter.close()
    }
}
