package com.kdev66.ecoscan.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Détecteur de déchets embarqué (YOLOv8n, LiteRT).
 *
 * Mesures sur l'appareil de référence (Redmi Note 8, entrée 640 px, médiane de 5 passages, 6 novembre 2026) :
 * GPU 336 ms ; NNAPI 556 ms ; CPU générique 4 threads 645 ms ; XNNPACK 925 ms, car l'API Java de LiteRT 1.4.2
 * applique XNNPACK sans tenir compte du nombre de threads. D'où le choix par défaut : GPU, repli sur le CPU générique.
 * Voir docs/PRD.md § 11.
 *
 * Non réentrant : les appels à [detect] sont sérialisés. Avec le GPU, créer et utiliser le détecteur sur le même
 * thread (le contexte OpenGL éventuel y est attaché) : l'application le fait sur un exécuteur dédié à un seul thread.
 */
class WasteDetector(
    context: Context,
    val acceleration: Acceleration,
    modelAsset: String = MODEL_ASSET,
    numThreads: Int = 4,
    confidenceThreshold: Float = 0.25f,
) : Closeable {

    /** Moteur d'exécution du modèle. */
    enum class Acceleration {
        /** Délégué GPU de LiteRT (OpenCL ou OpenGL ES), avec cache des programmes compilés. */
        GPU,
        /** CPU avec les noyaux génériques de LiteRT (XNNPACK désactivé) : repli par défaut. */
        CPU,
        /** CPU avec XNNPACK (choix par défaut de LiteRT, mono-thread en pratique ici). Gardé pour les mesures. */
        XNNPACK,
        /** NNAPI d'Android (pilote du constructeur ; déprécié depuis Android 15). Gardé pour les mesures. */
        NNAPI,
    }

    private val delegate: Delegate? = when (acceleration) {
        Acceleration.GPU -> GpuDelegate(
            GpuDelegate.Options().apply {
                // Les programmes GPU compilés sont gardés en cache : l'initialisation suivante est bien plus courte.
                setSerializationParams(context.codeCacheDir.absolutePath, MODEL_VERSION)
                setInferencePreference(GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
            },
        )
        Acceleration.NNAPI -> NnApiDelegate()
        Acceleration.CPU, Acceleration.XNNPACK -> null
    }
    private val interpreter: Interpreter
    val inputSize: Int
    private val postProcessor: YoloPostProcessor

    private val inputBitmap: Bitmap
    private val inputCanvas: Canvas
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixels: IntArray
    private val inputFloats: FloatArray
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer
    private val outputFloats: FloatArray

    init {
        val options = Interpreter.Options()
            .setNumThreads(numThreads)
            .setUseXNNPACK(acceleration == Acceleration.XNNPACK)
        delegate?.let { options.addDelegate(it) }
        interpreter = try {
            Interpreter(loadModel(context, modelAsset), options)
        } catch (e: Exception) {
            delegate?.close()
            throw e
        }

        val inShape = interpreter.getInputTensor(0).shape()     // [1, 640, 640, 3]
        val outShape = interpreter.getOutputTensor(0).shape()   // [1, 4 + classes, ancres]
        check(inShape.size == 4 && inShape[1] == inShape[2] && inShape[3] == 3) {
            "entrée inattendue : ${inShape.contentToString()}"
        }
        inputSize = inShape[1]
        val numClasses = outShape[1] - 4
        val numAnchors = outShape[2]
        check(numClasses == WasteClass.entries.size) {
            "le modèle a $numClasses classes, l'application en connaît ${WasteClass.entries.size}"
        }
        postProcessor = YoloPostProcessor(numClasses, numAnchors, confidenceThreshold)

        inputBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        inputCanvas = Canvas(inputBitmap)
        pixels = IntArray(inputSize * inputSize)
        inputFloats = FloatArray(inputSize * inputSize * 3)
        inputBuffer = ByteBuffer.allocateDirect(inputFloats.size * 4).order(ByteOrder.nativeOrder())
        outputFloats = FloatArray(outShape[1] * numAnchors)
        outputBuffer = ByteBuffer.allocateDirect(outputFloats.size * 4).order(ByteOrder.nativeOrder())
    }

    /** Analyse une image déjà remise à l'endroit (ARGB_8888, en mémoire logicielle). */
    @Synchronized
    fun detect(image: Bitmap): DetectionResult {
        val start = SystemClock.elapsedRealtime()
        val lb = Letterbox.compute(image.width, image.height, inputSize)

        // Letterbox : fond gris 114 (valeur d'Ultralytics), image centrée sans déformation.
        inputCanvas.drawColor(PAD_COLOR)
        val dst = RectF(lb.padX, lb.padY, lb.padX + image.width * lb.scale, lb.padY + image.height * lb.scale)
        inputCanvas.drawBitmap(image, null, dst, scalePaint)

        // RVB normalisé entre 0 et 1, ordre NHWC.
        inputBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        var j = 0
        for (p in pixels) {
            inputFloats[j++] = ((p shr 16) and 0xFF) / 255f
            inputFloats[j++] = ((p shr 8) and 0xFF) / 255f
            inputFloats[j++] = (p and 0xFF) / 255f
        }
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(inputFloats)

        val inferenceStart = SystemClock.elapsedRealtime()

        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(outputFloats)
        val postStart = SystemClock.elapsedRealtime()

        val detections = postProcessor.process(outputFloats, lb)
        val end = SystemClock.elapsedRealtime()
        val timings = DetectionTimings(inferenceStart - start, postStart - inferenceStart, end - postStart)
        return DetectionResult(detections, image.width, image.height, timings)
    }

    override fun close() {
        interpreter.close()
        delegate?.close()
    }

    companion object {
        private const val TAG = "EcoScan"

        /**
         * Le meilleur détecteur disponible sur l'appareil : GPU si pris en charge et s'il s'initialise,
         * sinon CPU générique sur 4 threads. À appeler hors du thread principal (jusqu'à 5 s au premier lancement).
         */
        fun createBest(context: Context): WasteDetector {
            val gpuSupported = CompatibilityList().use { it.isDelegateSupportedOnThisDevice }
            if (gpuSupported) {
                try {
                    return WasteDetector(context, Acceleration.GPU)
                } catch (e: Exception) {
                    Log.w(TAG, "GPU indisponible, repli sur le CPU", e)
                }
            }
            return WasteDetector(context, Acceleration.CPU)
        }

        const val MODEL_ASSET = "ecoscan_detector_v1.tflite"
        /** Identifiant enregistré avec chaque signalement (étape 2). */
        const val MODEL_VERSION = "yolov8n-tunmarinelitter-v1-640-fp32"
        private const val PAD_COLOR = 0xFF727272.toInt()

        private fun loadModel(context: Context, asset: String): MappedByteBuffer =
            context.assets.openFd(asset).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}
