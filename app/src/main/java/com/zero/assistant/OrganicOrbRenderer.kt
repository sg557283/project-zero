package com.zero.assistant

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class OrganicOrbRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private val mesh = OrganicOrbGeometry.create(4)
    private val vertices = FloatBuffer.allocate(mesh.vertices.size)
    private val particles = FloatBuffer.allocate(mesh.particles.size)
    private val edgeBuffer = createShortBuffer(mesh.edges)
    private val stateActivity = AtomicInteger(0)
    private val stateStrength = java.util.concurrent.atomic.AtomicReference(0.8f)
    private val reducedMotion = AtomicBoolean(false)
    private var surfaceReady = false
    private var meshProgram = 0
    private var coreProgram = 0
    private var particleProgram = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val modelViewProjection = FloatArray(16)
    private val startNanos = SystemClock.elapsedRealtimeNanos()

    fun setState(activity: Int, strength: Float, reduced: Boolean, publishedNanos: Long) {
        stateActivity.set(activity)
        stateStrength.set(strength)
        reducedMotion.set(reduced)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        meshProgram = createProgram(MESH_VERTEX, MESH_FRAGMENT)
        coreProgram = createProgram(CORE_VERTEX, CORE_FRAGMENT)
        particleProgram = createProgram(PARTICLE_VERTEX, PARTICLE_FRAGMENT)
        surfaceReady = meshProgram != 0 && coreProgram != 0 && particleProgram != 0
        vertices.clear(); vertices.put(mesh.vertices); vertices.position(0)
        particles.clear(); particles.put(mesh.particles); particles.position(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.frustumM(projection, 0, -aspect * 0.68f, aspect * 0.68f, -0.68f, 0.68f, 2.4f, 7f)
        Matrix.setLookAtM(view, 0, 0f, 0f, 4.1f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!surfaceReady) return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val elapsed = if (reducedMotion.get()) 0f else ((SystemClock.elapsedRealtimeNanos() - startNanos) / 1e9f)
        val rotation = elapsed * 0.25f
        Matrix.setIdentityM(MODEL, 0)
        Matrix.rotateM(MODEL, 0, rotation.toFloat() * 57.2958f, 0.15f, 1f, 0.12f)
        Matrix.multiplyMM(modelViewProjection, 0, view, 0, MODEL, 0)
        Matrix.multiplyMM(TEMP, 0, projection, 0, modelViewProjection, 0)
        System.arraycopy(TEMP, 0, modelViewProjection, 0, 16)

        drawCore(elapsed)
        drawMesh(elapsed)
        drawParticles(elapsed)
    }

    private fun drawCore(time: Float) {
        GLES20.glUseProgram(coreProgram)
        val position = GLES20.glGetAttribLocation(coreProgram, "aPosition")
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, CORE_BUFFER)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(coreProgram, "uMvp"), 1, false, modelViewProjection, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(coreProgram, "uTime"), time)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(coreProgram, "uActivity"), stateActivity.get().toFloat())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 110)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawMesh(time: Float) {
        GLES20.glUseProgram(meshProgram)
        val position = GLES20.glGetAttribLocation(meshProgram, "aPosition")
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, vertices)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(meshProgram, "uMvp"), 1, false, modelViewProjection, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uTime"), time)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uStrength"), stateStrength.get())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uActivity"), stateActivity.get().toFloat())
        GLES20.glLineWidth(1f)
        edgeBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_LINES, mesh.edges.size, GLES20.GL_UNSIGNED_SHORT, edgeBuffer)
    }

    private fun drawParticles(time: Float) {
        GLES20.glUseProgram(particleProgram)
        val position = GLES20.glGetAttribLocation(particleProgram, "aPosition")
        GLES20.glVertexAttribPointer(position, 4, GLES20.GL_FLOAT, false, 16, particles)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(particleProgram, "uMvp"), 1, false, modelViewProjection, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(particleProgram, "uTime"), time)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 48)
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        val vertexId = compile(GLES20.GL_VERTEX_SHADER, vertex) ?: return 0
        val fragmentId = compile(GLES20.GL_FRAGMENT_SHADER, fragment) ?: return 0
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexId)
        GLES20.glAttachShader(program, fragmentId)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) return 0
        return program
    }

    private fun compile(type: Int, source: String): Int? {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        return if (compiled[0] == 0) null else shader
    }

    private fun createShortBuffer(values: IntArray): ShortBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        for (value in values) buffer.put(value.toShort())
        buffer.position(0)
        return buffer
    }

    private companion object {
        val MODEL = FloatArray(16)
        val TEMP = FloatArray(16)
        val CORE_BUFFER = floatArrayOf(0f, 0f, -0.1f).let { center ->
            val vertices = mutableListOf<Float>()
            vertices += center[0]; vertices += center[1]; vertices += center[2]
            repeat(109) { index ->
                val angle = (index - 1).toFloat() / 108f * PI.toFloat() * 2f
                vertices += cos(angle) * 0.82f; vertices += sin(angle) * 0.82f; vertices += -0.12f
            }
            val values = vertices.toFloatArray()
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(values).apply { position(0) }
        }

        val MESH_VERTEX = """
            precision highp float;
            attribute vec3 aPosition;
            uniform mat4 uMvp;
            uniform float uTime;
            uniform float uStrength;
            varying vec3 vPosition;
            varying float vDepth;
            vec3 organic(vec3 p) {
                float wave = sin(p.x * 3.1 + uTime * 0.8) + sin(p.y * 4.2 - uTime * 0.55) + cos(p.z * 3.7 + p.x * 1.4);
                float macro = sin((p.x + p.y * 0.65) * 2.15 + uTime * 0.29);
                float ridge = cos((p.z * 3.4 - p.y * 1.25) + uTime * 0.41);
                float radius = 1.0 + uStrength * 0.018 * wave + 0.018 * macro + 0.012 * ridge;
                return normalize(p) * radius;
            }
            void main() {
                vec3 p = organic(aPosition);
                vPosition = p;
                vec4 view = uMvp * vec4(p * 0.82, 1.0);
                vDepth = view.z;
                gl_Position = view;
            }
        """.trimIndent()
        val MESH_FRAGMENT = """
            precision mediump float;
            varying vec3 vPosition;
            varying float vDepth;
            uniform float uActivity;
            uniform float uTime;
            vec3 palette(vec3 p) {
                float side = smoothstep(-0.8, 0.8, p.x);
                float vertical = smoothstep(-0.8, 0.8, p.y);
                vec3 left = mix(vec3(0.02,0.17,1.0), vec3(0.10,0.85,1.0), smoothstep(-0.75,-0.05,p.x));
                vec3 right = mix(vec3(0.58,0.22,1.0), vec3(1.0,0.17,0.74), smoothstep(-0.2,0.75,p.x));
                vec3 color = mix(left, right, side);
                color = mix(color, vec3(0.35,0.18,1.0), (1.0-vertical)*0.18);
                float lowerRight = smoothstep(-0.15, 0.82, p.x) * smoothstep(-0.75, -0.05, -p.y);
                color = mix(color, vec3(1.0, 0.17, 0.72), lowerRight * 0.72);
                float topViolet = smoothstep(0.12, 0.85, p.y) * smoothstep(-0.35, 0.85, p.x);
                color = mix(color, vec3(0.58, 0.24, 1.0), topViolet * 0.58);
                return color;
            }
            void main() {
                float edge = 0.72 + 0.28 * sin((vPosition.x + vPosition.y) * 7.0 + uTime);
                vec3 color = palette(vPosition) * edge;
                float depth = smoothstep(-2.2, 1.0, vDepth);
                gl_FragColor = vec4(color, (0.30 + 0.48 * depth));
            }
        """.trimIndent()
        val CORE_VERTEX = """
            precision highp float;
            attribute vec3 aPosition;
            uniform mat4 uMvp;
            varying vec2 vUv;
            void main() { vUv = aPosition.xy / 0.82; gl_Position = uMvp * vec4(aPosition, 1.0); }
        """.trimIndent()
        val CORE_FRAGMENT = """
            precision mediump float;
            varying vec2 vUv;
            uniform float uTime;
            uniform float uActivity;
            void main() {
                float radius = length(vUv);
                float alpha = smoothstep(1.0, 0.18, radius) * smoothstep(1.0, 0.45, radius);
                vec3 center = vec3(0.018,0.025,0.055);
                gl_FragColor = vec4(center, alpha * 0.92);
            }
        """.trimIndent()
        val PARTICLE_VERTEX = """
            precision highp float;
            attribute vec4 aPosition;
            uniform mat4 uMvp;
            uniform float uTime;
            varying float vAlpha;
            void main() {
                vec3 p = aPosition.xyz;
                p += vec3(sin(uTime * 0.25 + p.y * 8.0), cos(uTime * 0.19 + p.x * 7.0), sin(uTime * 0.22 + p.z * 9.0)) * 0.018;
                gl_Position = uMvp * vec4(p, 1.0);
                gl_PointSize = aPosition.w * 1.8;
                vAlpha = 0.20 + 0.18 * sin(uTime * 0.9 + p.x * 5.0);
            }
        """.trimIndent()
        val PARTICLE_FRAGMENT = """
            precision mediump float;
            varying float vAlpha;
            void main() {
                vec2 d = gl_PointCoord - vec2(0.5);
                float a = smoothstep(0.25, 0.0, length(d));
                gl_FragColor = vec4(0.48,0.72,1.0,a*vAlpha);
            }
        """.trimIndent()
    }
}
