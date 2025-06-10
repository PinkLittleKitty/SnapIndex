import tensorflow as tf

# Load original TFLite model
with open("openai_clip.tflite", "rb") as f:
    model_content = f.read()

# Initialize converter from saved model or TFLite flatbuffer
# Since you have a tflite file, use the TFLiteConverter.from_saved_model or from ConcreteFunctions for original models
# For tflite flatbuffer, you need to convert from the original model (saved_model or .pb).
# If you only have tflite, you may need the original SavedModel for quantization.
# Assuming you have SavedModel folder:
converter = tf.lite.TFLiteConverter.from_saved_model("path/to/saved_model")

# Enable dynamic range quantization
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# Convert the model
tflite_quant_model = converter.convert()

# Save quantized model
with open("openai_clip_dynamic_quant.tflite", "wb") as f:
    f.write(tflite_quant_model)
