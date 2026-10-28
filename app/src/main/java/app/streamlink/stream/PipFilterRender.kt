package app.streamlink.stream

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PT: Filtro que desenha uma segunda câmera sobre o vídeo principal.
 *
 * PT: O RootEncoder desenha todos os filtros em um único quad de tela cheia, então
 * PT: este filtro faz tudo em um shader: amostra o vídeo anterior e mistura a
 * PT: textura da segunda câmera apenas dentro do retângulo configurado.
 *
 * PT: Diferente do `SurfaceFilterRender` da biblioteca, a rotação aqui é aplicada
 * PT: nas coordenadas de tela (e não nas coordenadas de textura), o que mantém a
 * PT: janela sem distorção em qualquer proporção.
 *
 * EN: Filter that draws a second camera over the main video.
 *
 * EN: RootEncoder draws all filters in a single full-screen quad, so
 * EN: this filter does everything in one shader: samples the previous video and blends
 * EN: the second camera texture only inside the configured rectangle.
 *
 * EN: Unlike the library's `SurfaceFilterRender`, the rotation here is applied
 * EN: in screen coordinates (not texture coordinates), which keeps the
 * EN: window undistorted at any aspect ratio.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class PipFilterRender(
    private val onSurfaceReady: (SurfaceTexture) -> Unit
) : BaseFilterRender() {

    /**
     * PT: Textura criada pelo GL; a segunda câmera escreve nela.
     * EN: Texture created by the GL; the second camera writes to it.
     */
    @Volatile
    var surfaceTexture: SurfaceTexture? = null
        private set

    /**
     * PT: Surface entregue ao Camera2 da segunda câmera.
     * EN: Surface handed to the second camera's Camera2.
     */
    @Volatile
    var surface: Surface? = null
        private set

    /**
     * PT: O RootEncoder libera o filtro ao reiniciar o GL; não pode ser reutilizado.
     * EN: RootEncoder releases the filter when restarting the GL; it cannot be reused.
     */
    @Volatile
    var isReleased: Boolean = false
        private set

    // PT: Layout em percentual da imagem final (0–100), canto superior esquerdo.
    // EN: Layout as a percentage of the final image (0–100), top-left corner.
    @Volatile private var leftPercent = 0f
    @Volatile private var topPercent = 0f
    @Volatile private var boxWidthPercent = 30f
    @Volatile private var boxHeightPercent = 30f
    @Volatile private var rotationDegrees = 90

    private val objectTextureId = intArrayOf(-1)
    private var program = -1
    private var aPosition = -1
    private var aTexture = -1
    private var uMvpMatrix = -1
    private var uSampler = -1
    private var uObject = -1
    private var uObjectMatrix = -1
    private var uCanvasSize = -1
    private var uCenter = -1
    private var uQuadSize = -1
    private var uAngle = -1

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
        Matrix.setIdentityM(MVPMatrix, 0)
        Matrix.setIdentityM(STMatrix, 0)
    }

    /**
     * PT: Define a janela no vídeo final.
     *
     * PT: @param leftPercent distância da borda esquerda (0–100)
     * PT: @param topPercent distância do topo (0–100)
     * PT: @param widthPercent largura da janela (0–100)
     * PT: @param heightPercent altura da janela (0–100)
     * PT: @param rotationDegrees rotação da imagem em graus (0, 90, 180 ou 270)
     *
     * EN: Defines the window in the final video.
     *
     * EN: @param leftPercent distance from the left edge (0–100)
     * EN: @param topPercent distance from the top (0–100)
     * EN: @param widthPercent window width (0–100)
     * EN: @param heightPercent window height (0–100)
     * EN: @param rotationDegrees image rotation in degrees (0, 90, 180 or 270)
     */
    fun setLayout(
        leftPercent: Float,
        topPercent: Float,
        widthPercent: Float,
        heightPercent: Float,
        rotationDegrees: Int
    ) {
        this.leftPercent = leftPercent
        this.topPercent = topPercent
        this.boxWidthPercent = widthPercent
        this.boxHeightPercent = heightPercent
        this.rotationDegrees = rotationDegrees
    }

    override fun initGlFilter(context: Context) {
        program = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexture = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMvpMatrix = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSampler = GLES20.glGetUniformLocation(program, "uSampler")
        uObject = GLES20.glGetUniformLocation(program, "uObject")
        uObjectMatrix = GLES20.glGetUniformLocation(program, "uObjectMatrix")
        uCanvasSize = GLES20.glGetUniformLocation(program, "uCanvasSize")
        uCenter = GLES20.glGetUniformLocation(program, "uCenter")
        uQuadSize = GLES20.glGetUniformLocation(program, "uQuadSize")
        uAngle = GLES20.glGetUniformLocation(program, "uAngle")

        GlUtil.createExternalTextures(1, objectTextureId, 0)
        val texture = SurfaceTexture(objectTextureId[0])
        texture.setDefaultBufferSize(
            getWidth().coerceAtLeast(1),
            getHeight().coerceAtLeast(1)
        )
        surfaceTexture = texture
        surface = Surface(texture)
        Handler(Looper.getMainLooper()).post { onSurfaceReady(texture) }
    }

    override fun drawFilter() {
        val texture = surfaceTexture ?: return
        runCatching { texture.updateTexImage() }
        texture.getTransformMatrix(STMatrix)

        val canvasWidth = getWidth().toFloat().coerceAtLeast(1f)
        val canvasHeight = getHeight().toFloat().coerceAtLeast(1f)

        val boxWidth = boxWidthPercent.coerceIn(0f, 100f) / 100f * canvasWidth
        val boxHeight = boxHeightPercent.coerceIn(0f, 100f) / 100f * canvasHeight
        val centerX = leftPercent.coerceIn(0f, 100f) / 100f * canvasWidth + boxWidth / 2f
        // PT: O layout usa o topo como origem; o shader usa Y para cima.
        // EN: The layout uses the top as origin; the shader uses Y upwards.
        val centerY = canvasHeight -
            (topPercent.coerceIn(0f, 100f) / 100f * canvasHeight + boxHeight / 2f)

        val normalized = ((rotationDegrees % 360) + 360) % 360
        val swap = normalized == 90 || normalized == 270
        // PT: Dimensões do quad antes da rotação. A caixa final é o retângulo
        // PT: delimitador depois de girar, por isso a troca em 90/270.
        // EN: Quad dimensions before rotation. The final box is the bounding
        // EN: rectangle after rotating, hence the swap at 90/270.
        val quadWidth = if (swap) boxHeight else boxWidth
        val quadHeight = if (swap) boxWidth else boxHeight
        val angle = Math.toRadians(normalized.toDouble()).toFloat()

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

        GLES20.glUniform1i(uObject, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, objectTextureId[0])

        GLES20.glUniformMatrix4fv(uObjectMatrix, 1, false, STMatrix, 0)
        GLES20.glUniform2f(uCanvasSize, canvasWidth, canvasHeight)
        GLES20.glUniform2f(uCenter, centerX, centerY)
        GLES20.glUniform2f(uQuadSize, quadWidth, quadHeight)
        GLES20.glUniform1f(uAngle, angle)
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
        if (objectTextureId[0] != -1) {
            GLES20.glDeleteTextures(1, objectTextureId, 0)
            objectTextureId[0] = -1
        }
        runCatching { surface?.release() }
        runCatching { surfaceTexture?.release() }
        surface = null
        surfaceTexture = null
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 vTextureCoord;
            varying vec2 vScreenCoord;

            void main() {
              gl_Position = uMVPMatrix * aPosition;
              vTextureCoord = aTextureCoord.xy;
              vScreenCoord = aTextureCoord.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;

            uniform sampler2D uSampler;
            uniform samplerExternalOES uObject;
            uniform mat4 uObjectMatrix;
            uniform vec2 uCanvasSize;
            uniform vec2 uCenter;
            uniform vec2 uQuadSize;
            uniform float uAngle;

            varying vec2 vTextureCoord;
            varying vec2 vScreenCoord;

            void main() {
              vec4 base = texture2D(uSampler, vTextureCoord);
              vec2 pixel = vec2(vScreenCoord.x * uCanvasSize.x, vScreenCoord.y * uCanvasSize.y);
              vec2 delta = pixel - uCenter;
              float c = cos(uAngle);
              float s = sin(uAngle);
              // PT: Rotaciona de volta para o espaço da janela (Y para cima).
              // EN: Rotates back into window space (Y up).
              vec2 local = vec2(delta.x * c + delta.y * s, -delta.x * s + delta.y * c);
              vec2 uv = local / uQuadSize + 0.5;
              if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                gl_FragColor = base;
              } else {
                vec2 mapped = (uObjectMatrix * vec4(uv, 0.0, 1.0)).xy;
                vec4 camera = texture2D(uObject, mapped);
                gl_FragColor = mix(base, camera, camera.a);
              }
            }
        """
    }
}
