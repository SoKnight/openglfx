package com.huskerdev.openglfx.internal.canvas

import com.huskerdev.grapl.gl.GLProfile
import com.huskerdev.openglfx.*
import com.huskerdev.openglfx.canvas.GLCanvas
import com.huskerdev.openglfx.internal.GLFXUtils
import com.huskerdev.openglfx.internal.NGGLCanvas

import com.huskerdev.openglfx.internal.Framebuffer
import com.sun.prism.PixelFormat


import com.sun.prism.Texture
import java.nio.ByteBuffer


open class BlitCanvas(
    canvas: GLCanvas,
    executor: GLExecutor,
    profile: GLProfile
) : NGGLCanvas(canvas, executor, profile) {

    override fun onRenderThreadInit() = Unit
    override fun createSwapBuffer() = BlitSwapBuffer()

    protected inner class BlitSwapBuffer: SwapBuffer() {
        private lateinit var fbo: Framebuffer
        private lateinit var interopFBO: Framebuffer.Default
        private lateinit var dataBuffer: ByteBuffer

        private lateinit var fxTexture: Texture
        private var contentChanged = false

        override fun render(width: Int, height: Int) {
            if(checkFramebufferSize(width, height))
                canvas.fireReshapeEvent(width, height)

            fbo.bindFramebuffer()
            canvas.fireRenderEvent(fbo.id)
            fbo.blitTo(interopFBO)

            interopFBO.readPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, dataBuffer)
            contentChanged = true
        }

        private fun checkFramebufferSize(width: Int, height: Int): Boolean{
            if(!this::fbo.isInitialized || fbo.width != width || fbo.height != height){
                dispose()

                dataBuffer = GLFXUtils.createDirectBuffer(width * height * 4)
                interopFBO = Framebuffer.Default(width, height)
                fbo = createFramebufferForRender(width, height)
                return true
            }
            return false
        }

        override fun getTextureForDisplay(): Texture {
            val width = fbo.width
            val height = fbo.height

            if(!this::fxTexture.isInitialized || fxTexture.physicalWidth != width || fxTexture.physicalHeight != height){
                disposeFXResources()

                fxTexture = GLFXUtils.createPermanentFXTexture(width, height)
            }

            if(contentChanged) {
                contentChanged = false
                fxTexture.update(
                    dataBuffer, PixelFormat.BYTE_BGRA_PRE,
                    0, 0,
                    0, 0,
                    width, height,
                    width * 4, true
                )
            }

            return fxTexture
        }

        override fun dispose() {
            if (this::fbo.isInitialized) fbo.delete()
            if (this::interopFBO.isInitialized) interopFBO.delete()
            if (this::dataBuffer.isInitialized) GLFXUtils.cleanDirectBuffer(dataBuffer)
        }

        override fun disposeFXResources() {
            if (this::fxTexture.isInitialized) fxTexture.dispose()
        }
    }
}