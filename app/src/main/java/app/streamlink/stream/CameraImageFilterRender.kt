package app.streamlink.stream

import android.content.Context
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PT: Filtro OpenGL que aplica brilho e contraste ao vídeo e ao preview.
 *
 * PT: O RootEncoder desenha os filtros em sequência, cada um recebendo a textura
 * PT: do anterior. Este filtro faz as duas operações num só shader, o que evita
 * PT: duas passagens extras e mantém as cores consistentes entre preview,
 * PT: transmissão e gravação.
 *
 * EN: OpenGL filter that applies brightness and contrast to the video and preview.
 *
 * EN: RootEncoder draws filters in sequence, each one receiving the previous
 * EN: texture. This filter does both operations in a single shader, which avoids
 * EN: two extra passes and keeps colours consistent across preview, stream and
 * EN: recording.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class CameraImageFilterRender : BaseFilterRender() {

    /**
     * PT: O RootEncoder libera o filtro ao reiniciar o GL; não pode ser reutilizado.
     * EN: RootEncoder releases the filter when restarting the GL; it cannot be reused.
     */
    @Volatile
    var isReleased: Boolean = false
        private set

    @Volatile
    private var brightness = 0f

    @Volatile
    private var contrast = 1f

    private var program = -1
    private var aPosition = -1
    private var aTexture = -1
    private var uMvpMatrix = -1
    private var uSampler = -1
    private var uBrightness = -1
    private var uContrast = -1

    init {
        val vertices = floatArrayOf(
            // PT: X, Y, Z, U, V
            // EN: X, Y, Z, U, V
            -1f, -1f, 0f, 0f, 0f, // PT: inferior esquerdo / EN: bottom left
            1f, -1f, 0f, 1f, 0f, // PT: inferior direito / EN: bottom right
            -1f, 1f, 0f, 0f, 1f, // PT: superior esquerdo / EN: top left
            1f, 1f, 0f, 1f, 1f // PT: superior direito / EN: top right
        )
        squareVertex = ByteBuffer.allocateDirect(vertices.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        squareVertex.put(vertices).position(0)
    }

    /**
     * PT: Atualiza os valores em tempo real, sem reiniciar o GL.
     *
     * PT: @param brightness de -1 (mais escuro) a 1 (mais claro)
     * PT: @param contrast de 0.5 (mais suave) a 1.5 (mais forte)
     *
     * EN: Updates the values in real time without restarting the GL.
     *
     * EN: @param brightness from -1 (darker) to 1 (brighter)
     * EN: @param contrast from 0.5 (softer) to 1.5 (stronger)
     */
    fun setValues(brightness: Float, contrast: Float) {
        this.brightness = brightness.coerceIn(-1f, 1f)
        this.contrast = contrast.coerceIn(0.5f, 1.5f)
    }

    override fun initGlFilter(context: Context) {
        program = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexture = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMvpMatrix = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSampler = GLES20.glGetUniformLocation(program, "uSampler")
        uBrightness = GLES20.glGetUniformLocation(program, "uBrightness")
        uContrast = GLES20.glGetUniformLocation(program, "uContrast")
    }

    override fun drawFilter() {
        GLES20.glUseProgram(program)

        squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            aPosition, 3, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex
        )
        GLES20.glEnableVertexAttribArray(aPosition)

        squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            aTexture, 2, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex
        )
        GLES20.glEnableVertexAttribArray(aTexture)

        GLES20.glUniformMatrix4fv(uMvpMatrix, 1, false, MVPMatrix, 0)
        GLES20.glUniform1i(uSampler, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
        // PT: O brilho usa meia escala para o curso completo do slider não
        // PT: estourar as cores; o contraste é aplicado em torno do cinzento.
        // EN: Brightness uses half scale so the full slider range does not blow
        // EN: the colours out; contrast is applied around mid-grey.
        GLES20.glUniform1f(uBrightness, brightness * BRIGHTNESS_SCALE)
        GLES20.glUniform1f(uContrast, contrast)
    }

    override fun disableResources() {
        GlUtil.disableResources(aTexture, aPosition)
    }

    override fun release() {
        if (isReleased) return
        isReleased = true
        if (program != -1) {
            GLES20.glDeleteProgram(program)
            program = -1
        }
    }

    companion object {
        /**
         * PT: Fator do brilho em relação ao valor guardado (0–1).
         * EN: Brightness factor relative to the stored value (0–1).
         */
        private const val BRIGHTNESS_SCALE = 0.5f

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 vTextureCoord;

            void main() {
              gl_Position = uMVPMatrix * aPosition;
              vTextureCoord = aTextureCoord.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;

            uniform sampler2D uSampler;
            uniform float uBrightness;
            uniform float uContrast;

            varying vec2 vTextureCoord;

            void main() {
              vec4 pixel = texture2D(uSampler, vTextureCoord);
              vec3 adjusted = (pixel.rgb - vec3(0.5)) * uContrast + vec3(0.5);
              adjusted += vec3(uBrightness);
              gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), pixel.a);
            }
        """
    }
}
