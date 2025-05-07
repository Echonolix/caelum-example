package net.echonolix.vktest

import net.echonolix.caelum.*
import net.echonolix.caelum.glfw.consts.GLFW_CLIENT_API
import net.echonolix.caelum.glfw.consts.GLFW_FALSE
import net.echonolix.caelum.glfw.consts.GLFW_NO_API
import net.echonolix.caelum.glfw.consts.GLFW_RESIZABLE
import net.echonolix.caelum.glfw.functions.*
import net.echonolix.caelum.vulkan.*
import net.echonolix.caelum.vulkan.enums.*
import net.echonolix.caelum.vulkan.flags.*
import net.echonolix.caelum.vulkan.handles.*
import net.echonolix.caelum.vulkan.structs.*
import net.echonolix.caelum.vulkan.unions.VkClearValue
import net.echonolix.caelum.vulkan.unions.color
import net.echonolix.caelum.vulkan.unions.float32
import net.echonolix.vktest.utils.AverageCounter
import java.lang.foreign.MemorySegment
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class VkTest

fun loadLibrary(name: String) {
    System.load(Path("$name.dll").absolutePathString())
}

@OptIn(UnsafeAPI::class)
fun main() {
    loadLibrary("glfw3")

    MemoryStack {
        // region Init GLFW
        glfwInit()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        val width = 800
        val height = 600
        val window = glfwCreateWindow(width, height, "Vulkan".c_str(), nullptr(), nullptr())
        // endregion

        val useValidationLayer = false

        val layers = if (useValidationLayer) {
            setOf("VK_LAYER_KHRONOS_validation")
        } else {
            emptySet()
        }
        val extensions = buildSet {
            val count = NUInt32.malloc()
            val buffer = glfwGetRequiredInstanceExtensions(count.ptr())
            repeat(count.value.toInt()) {
                add(buffer[it].string)
            }
        } + setOf(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
        println(extensions)

        val appInfo = VkApplicationInfo.allocate().apply {
            pApplicationName = "Hello Vulkan".c_str()
            applicationVersion = VkApiVersion(0u, 1u, 0u, 0u).value
            pEngineName = "VK Test".c_str()
            engineVersion = VkApiVersion(0u, 1u, 0u, 0u).value
            apiVersion = VK_API_VERSION_1_0.value
        }

        val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate()
        populateDebugMessengerCreateInfo(debugCreateInfo)

        val createInfo = VkInstanceCreateInfo.allocate().apply {
            pApplicationInfo = appInfo.ptr()
            ppEnabledExtensionNames = extensions.c_strs()
            enabledExtensionCount = extensions.size.toUInt()
            ppEnabledLayerNames = layers.c_strs()
            enabledLayerCount = layers.size.toUInt()
            pNext = if (useValidationLayer) {
                debugCreateInfo.ptr()
            } else {
                nullptr()
            }
        }

        val instance = Vk.createInstance(createInfo.ptr(), null).getOrThrow()
        val debugUtilsMessenger = if (useValidationLayer) {
            instance.createDebugUtilsMessengerEXT(debugCreateInfo.ptr(), null).getOrThrow()
        } else {
            null
        }

        val physicalDevice = choosePhysicalDevice(instance)

        val physicalDeviceProperties = VkPhysicalDeviceProperties.allocate()
        val physicalDeviceFeatures = VkPhysicalDeviceFeatures.allocate()
        physicalDevice.getPhysicalDeviceProperties(physicalDeviceProperties.ptr())
        physicalDevice.getPhysicalDeviceFeatures(physicalDeviceFeatures.ptr())

        println("Using physical device ${physicalDeviceProperties.deviceName.string}")

        val surface = glfwCreateWindowSurface(instance, window, null).getOrThrow()
        var graphicsQueueFamilyIndex = -1
        MemoryStack {
            val queueFamilyPropertyCount = NUInt32.calloc()
            physicalDevice.getPhysicalDeviceQueueFamilyProperties(queueFamilyPropertyCount.ptr(), null)
            val queueFamilyProperties = VkQueueFamilyProperties.allocate(queueFamilyPropertyCount.value)
            physicalDevice.getPhysicalDeviceQueueFamilyProperties(
                queueFamilyPropertyCount.ptr(),
                queueFamilyProperties.ptr()
            )
            val isPresentSupported = NUInt32.malloc()
            repeat(queueFamilyPropertyCount.value.toInt()) {
                val queueFamilyProperty = queueFamilyProperties[it.toLong()]
                if (graphicsQueueFamilyIndex == -1 && queueFamilyProperty.queueFlags.contains(VkQueueFlags.GRAPHICS)) {
                    physicalDevice.getPhysicalDeviceSurfaceSupportKHR(it.toUInt(), surface, isPresentSupported.ptr())
                    check(isPresentSupported.value == 1u) { "Graphics queue family does not support present." }
                    graphicsQueueFamilyIndex = it
                }
            }
        }

        println("Queue Family: $graphicsQueueFamilyIndex")

        val queuePriority = NFloat.calloc().apply { value = 1f }
        val queueCreateInfos = VkDeviceQueueCreateInfo.allocate(1)
        queueCreateInfos[0].apply {
            queueFamilyIndex = graphicsQueueFamilyIndex.toUInt()
            queueCount = 1u
            pQueuePriorities = queuePriority.ptr()
        }
        val deviceExtensions = setOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
        val deviceCreateInfo = VkDeviceCreateInfo.allocate().apply {
            pQueueCreateInfos = queueCreateInfos.ptr()
            queueCreateInfoCount = 1u

            pEnabledFeatures = physicalDeviceFeatures.ptr()

            enabledExtensionCount = deviceExtensions.size.toUInt()
            ppEnabledExtensionNames = deviceExtensions.c_strs()

            enabledLayerCount = layers.size.toUInt()
            ppEnabledLayerNames = layers.c_strs()
        }
        val device = physicalDevice.createDevice(deviceCreateInfo.ptr(), null).getOrThrow()
        val graphicsQueueV = VkQueue.malloc()
        device.getDeviceQueue(graphicsQueueFamilyIndex.toUInt(), 0u, graphicsQueueV.ptr())
        val graphicsQueue = VkQueue.fromNativeData(device, graphicsQueueV.value)

        data class SwapchainSupportDetails(
            val capabilities: NValue<VkSurfaceCapabilitiesKHR>,
            val formats: List<NPointer<VkSurfaceFormatKHR>>,
            val presentModes: List<VkPresentModeKHR>
        )

        fun VkPhysicalDevice.querySwapchainSupport(): SwapchainSupportDetails {
            val capabilities = VkSurfaceCapabilitiesKHR.allocate()
            getPhysicalDeviceSurfaceCapabilitiesKHR(surface, capabilities.ptr())

            val formatCount = NUInt32.malloc()
            getPhysicalDeviceSurfaceFormatsKHR(surface, formatCount.ptr(), null)
            val formatsBuffer = VkSurfaceFormatKHR.allocate(formatCount.value)
            getPhysicalDeviceSurfaceFormatsKHR(surface, formatCount.ptr(), formatsBuffer.ptr())
            val formats = buildList {
                repeat(formatCount.value.toInt()) {
                    add(formatsBuffer[it.toLong()])
                }
            }

            val presentModeCount = NUInt32.malloc()
            getPhysicalDeviceSurfacePresentModesKHR(surface, presentModeCount.ptr(), null)
            val presentModesBuffer = VkPresentModeKHR.malloc(presentModeCount.value)
            getPhysicalDeviceSurfacePresentModesKHR(surface, presentModeCount.ptr(), presentModesBuffer.ptr())
            val presentModes = buildList {
                repeat(presentModeCount.value.toInt()) {
                    add(presentModesBuffer[it.toLong()])
                }
            }

            return SwapchainSupportDetails(capabilities, formats, presentModes)
        }

        fun chooseSwapchainFormat(formats: List<NPointer<VkSurfaceFormatKHR>>) = formats.find {
            it.format == VkFormat.R8G8B8A8_UNORM
        }

        fun choosePresentMode(modes: List<VkPresentModeKHR>) = modes.find {
            it == VkPresentModeKHR.IMMEDIATE_KHR
        } ?: VkPresentModeKHR.FIFO_KHR

        fun chooseSwapchainExtent(capabilities: NValue<VkSurfaceCapabilitiesKHR>): NPointer<VkExtent2D> {
            return if (capabilities.currentExtent.width != UInt.MAX_VALUE) capabilities.currentExtent
            else {
                val widthBuffer = NInt.malloc()
                val heightBuffer = NInt.malloc()
                glfwGetWindowSize(window, widthBuffer.ptr(), heightBuffer.ptr())

                val actualExtent = VkExtent2D.allocate().apply {
                    this.width = widthBuffer.value.toUInt()
                        .coerceIn(capabilities.minImageExtent.width, capabilities.maxImageExtent.width)
                    this.height = heightBuffer.value.toUInt()
                        .coerceIn(capabilities.minImageExtent.height, capabilities.maxImageExtent.height)
                }

                actualExtent.ptr()
            }
        }


        val swapchainSupport = physicalDevice.querySwapchainSupport()
        println("Supported swapchain Formats:")
        swapchainSupport.formats.forEach {
            println("${it.format}\t${it.colorSpace}")
        }
        println()
        println("Supported swapchain Present Modes:")
        swapchainSupport.presentModes.forEach {
            println(it)
        }
        println()

        val surfaceFormat = chooseSwapchainFormat(swapchainSupport.formats)!!
        println("Using swapchain format: ${surfaceFormat.format} ${surfaceFormat.colorSpace}")
        val presentMode = choosePresentMode(swapchainSupport.presentModes)
        println("Using swapchain present mode: $presentMode")
        val swapchainExtent = chooseSwapchainExtent(swapchainSupport.capabilities)

        val swapchainCreateInfo = VkSwapchainCreateInfoKHR.allocate().apply {
            this.surface = surface
            minImageCount = swapchainSupport.capabilities.minImageCount
            imageFormat = surfaceFormat.format
            imageColorSpace = surfaceFormat.colorSpace
            imageExtent = swapchainExtent
            imageArrayLayers = 1u
            imageUsage = VkImageUsageFlags.COLOR_ATTACHMENT

            imageSharingMode = VkSharingMode.EXCLUSIVE

            preTransform = swapchainSupport.capabilities.currentTransform
            compositeAlpha = VkCompositeAlphaFlagsKHR.OPAQUE_KHR
            this.presentMode = presentMode
            clipped = 1u
        }
        val swapchain = device.createSwapchainKHR(swapchainCreateInfo.ptr(), null).getOrThrow()

        val swapchainImageCount = NUInt32.malloc()
        device.getSwapchainImagesKHR(swapchain, swapchainImageCount.ptr(), null)
        println("Swapchain image count: ${swapchainImageCount.value}")
        val swapchainImages = buildList {
            val buffer = VkImage.malloc(swapchainImageCount.value)
            device.getSwapchainImagesKHR(swapchain, swapchainImageCount.ptr(), buffer.ptr())
            repeat(swapchainImageCount.value.toInt()) {
                add(VkImage.fromNativeData(device, buffer[it]))
            }
        }
        val swapchainImageFormat = surfaceFormat.format

        val swapchainImageViews = buildList {
            repeat(swapchainImageCount.value.toInt()) {
                val createInfo = VkImageViewCreateInfo.allocate().apply {
                    image = swapchainImages[it]
                    viewType = VkImageViewType.`2D`
                    format = swapchainImageFormat
                    components.r = VkComponentSwizzle.IDENTITY
                    components.g = VkComponentSwizzle.IDENTITY
                    components.b = VkComponentSwizzle.IDENTITY
                    components.a = VkComponentSwizzle.IDENTITY
                    subresourceRange.aspectMask = VkImageAspectFlags.COLOR
                    subresourceRange.baseMipLevel = 0u
                    subresourceRange.levelCount = 1u
                    subresourceRange.baseArrayLayer = 0u
                    subresourceRange.layerCount = 1u
                }
                add(device.createImageView(createInfo.ptr(), null).getOrThrow())
            }
        }

        fun VkDevice.makeShaderModule(code: ByteArray): VkShaderModule {
            val codeBuffer = NInt8.malloc(code.size)
            codeBuffer.segment.copyFrom(MemorySegment.ofArray(code))
            val createInfo = VkShaderModuleCreateInfo.allocate().apply {
                codeSize = code.size.toLong()
                pCode = reinterpretCast(codeBuffer.ptr())
            }
            return createShaderModule(createInfo.ptr(), null).getOrThrow()
        }

        val vktestSpvData = VkTest::class.java.getResource("/vktest.spv")!!.readBytes()
        val vkTestShaderModule = device.makeShaderModule(vktestSpvData)

        val shaderStages = VkPipelineShaderStageCreateInfo.allocate(2)
        shaderStages[0].apply {
            stage = VkShaderStageFlags.VERTEX
            module = vkTestShaderModule
            pName = "vertexMain".c_str()
        }
        shaderStages[1].apply {
            stage = VkShaderStageFlags.FRAGMENT
            module = vkTestShaderModule
            pName = "fragmentMain".c_str()
        }

        val vertexInputStateCreateInfo = VkPipelineVertexInputStateCreateInfo.allocate().apply {
            vertexBindingDescriptionCount = 0u
            pVertexBindingDescriptions = nullptr()
            vertexAttributeDescriptionCount = 0u
            pVertexAttributeDescriptions = nullptr()
        }

        val inputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.allocate().apply {
            topology = VkPrimitiveTopology.TRIANGLE_LIST
            primitiveRestartEnable = 0u
        }

        val viewport = VkViewport.allocate()
        viewport.x = 0.0f
        viewport.y = 0.0f
        viewport.width = swapchainExtent.width.toFloat()
        viewport.height = swapchainExtent.height.toFloat()
        viewport.minDepth = 0.0f
        viewport.maxDepth = 1.0f

        val scissor = VkRect2D.allocate()
        scissor.offset.x = 0
        scissor.offset.y = 0
        scissor.extent = swapchainExtent

        val viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.allocate().apply {
            viewportCount = 1u
            pViewports = viewport.ptr()
            scissorCount = 1u
            pScissors = scissor.ptr()
        }

        val rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.allocate().apply {
            depthClampEnable = VK_FALSE
            rasterizerDiscardEnable = VK_FALSE
            polygonMode = VkPolygonMode.FILL
            lineWidth = 1f
            cullMode = VkCullModeFlags.NONE
            frontFace = VkFrontFace.CLOCKWISE
            depthBiasEnable = VK_FALSE
            depthBiasConstantFactor = 0.0f // Optional
            depthBiasClamp = 0.0f // Optional
            depthBiasSlopeFactor = 0.0f // Optional
        }

        val multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.allocate().apply {
            sampleShadingEnable = VK_FALSE
            rasterizationSamples = VkSampleCountFlags.`1_BIT`
            minSampleShading = 1.0f // Optional
            pSampleMask = nullptr() // Optional
            alphaToCoverageEnable = VK_FALSE // Optional
            alphaToOneEnable = VK_FALSE // Optional
        }

        val colorBlendAttachmentState = VkPipelineColorBlendAttachmentState.allocate().apply {
            colorWriteMask =
                VkColorComponentFlags.R + VkColorComponentFlags.G + VkColorComponentFlags.B + VkColorComponentFlags.A
            blendEnable = VK_FALSE
            srcColorBlendFactor = VkBlendFactor.ONE // Optional
            dstColorBlendFactor = VkBlendFactor.ZERO // Optional
            colorBlendOp = VkBlendOp.ADD // Optional
            srcAlphaBlendFactor = VkBlendFactor.ONE // Optional
            dstAlphaBlendFactor = VkBlendFactor.ZERO // Optional
            alphaBlendOp = VkBlendOp.ADD // Optional
        }

        val colorBlendState = VkPipelineColorBlendStateCreateInfo.allocate().apply {
            logicOpEnable = VK_FALSE
            logicOp = VkLogicOp.COPY // Optional
            attachmentCount = 1u
            pAttachments = colorBlendAttachmentState.ptr()
            blendConstants[0] = 0.0f // Optional
            blendConstants[1] = 0.0f // Optional
            blendConstants[2] = 0.0f // Optional
            blendConstants[3] = 0.0f // Optional
        }

        val pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.allocate().apply {
            setLayoutCount = 0u // Optional
            pSetLayouts = nullptr() // Optional
            pushConstantRangeCount = 0u // Optional
            pPushConstantRanges = nullptr() // Optional
        }

        val pipelineLayout = device.createPipelineLayout(pipelineLayoutCreateInfo.ptr(), null).getOrThrow()

        val colorAttachment = VkAttachmentDescription.allocate().apply {
            format = swapchainImageFormat
            samples = VkSampleCountFlags.`1_BIT`
            loadOp = VkAttachmentLoadOp.CLEAR
            storeOp = VkAttachmentStoreOp.STORE
            stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
            stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
            initialLayout = VkImageLayout.UNDEFINED
            finalLayout = VkImageLayout.PRESENT_SRC_KHR
        }

        val colorAttachmentRef = VkAttachmentReference.allocate().apply {
            attachment = 0u
            layout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
        }

        val subpass = VkSubpassDescription.allocate().apply {
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
            colorAttachmentCount = 1u
            pColorAttachments = colorAttachmentRef.ptr()
        }

        val subpassDependency = VkSubpassDependency.allocate().apply {
            srcSubpass = VK_SUBPASS_EXTERNAL
            dstSubpass = 0u
            srcStageMask = VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT
            srcAccessMask = VkAccessFlags.NONE
            dstStageMask = VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT
            dstAccessMask = VkAccessFlags.COLOR_ATTACHMENT_WRITE
        }

        val renderPassCreateInfo = VkRenderPassCreateInfo.allocate().apply {
            attachmentCount = 1u
            pAttachments = colorAttachment.ptr()
            subpassCount = 1u
            pSubpasses = subpass.ptr()
            dependencyCount = 1u
            pDependencies = subpassDependency.ptr()
        }

        val renderPass = device.createRenderPass(renderPassCreateInfo.ptr(), null).getOrThrow()

        val pipelineCreateInfo = VkGraphicsPipelineCreateInfo.allocate().apply {
            stageCount = 2u
            pStages = shaderStages.ptr()
            pVertexInputState = vertexInputStateCreateInfo.ptr()
            pInputAssemblyState = inputAssemblyStateCreateInfo.ptr()
            pViewportState = viewportStateCreateInfo.ptr()
            pRasterizationState = rasterizationStateCreateInfo.ptr()
            pMultisampleState = multisampleStateCreateInfo.ptr()
            pDepthStencilState = nullptr()
            pColorBlendState = colorBlendState.ptr()
            pDynamicState = nullptr()
            layout = pipelineLayout
            this.renderPass = renderPass
            this.subpass = 0u
        }

        val pipeline = device.createGraphicsPipelines(
            VkPipelineCache.fromNativeData(device, 0L), 1u, pipelineCreateInfo.ptr(), null
        ).getOrThrow()

        val swapchainFramebuffers = buildList {
            repeat(swapchainImageViews.size) {
                val attachments = VkImageView.valueOf(swapchainImageViews[it])
                val framebufferCreateInfo = VkFramebufferCreateInfo.allocate().apply {
                    this.renderPass = renderPass
                    attachmentCount = 1u
                    pAttachments = attachments.ptr()
                    this.width = swapchainExtent.width
                    this.height = swapchainExtent.height
                    this.layers = 1u
                }
                add(device.createFramebuffer(framebufferCreateInfo.ptr(), null).getOrThrow())
            }
        }

        val commandPoolCreateInfo = VkCommandPoolCreateInfo.allocate().apply {
            flags = VkCommandPoolCreateFlags.RESET_COMMAND_BUFFER
            queueFamilyIndex = graphicsQueueFamilyIndex.toUInt()
        }

        val commandPool = device.createCommandPool(commandPoolCreateInfo.ptr(), null).getOrThrow()

        val commandBufferAllocateInfo = VkCommandBufferAllocateInfo.allocate().apply {
            this.commandPool = commandPool
            level = VkCommandBufferLevel.PRIMARY
            commandBufferCount = 1u
        }
        val semaphoreCreateInfo = VkSemaphoreCreateInfo.allocate().apply {}

        val fenceCreateInfo = VkFenceCreateInfo.allocate().apply {
            flags = VkFenceCreateFlags.SIGNALED
        }

        val clearColor = VkClearValue.malloc().apply {
            color.float32[0] = 0f
            color.float32[1] = 0f
            color.float32[2] = 0f
            color.float32[3] = 1f
        }

        class Frame {
            val pCommandBuffer = VkCommandBuffer.malloc()
            val commandBuffer: VkCommandBuffer

            init {
                device.allocateCommandBuffers(commandBufferAllocateInfo.ptr(), pCommandBuffer.ptr())
                commandBuffer = VkCommandBuffer.fromNativeData(commandPool, pCommandBuffer.value)
            }

            val renderFinishedSemaphore = device.createSemaphore(semaphoreCreateInfo.ptr(), null).getOrThrow()
            val pRenderFinishedSemaphore = VkSemaphore.valueOf(renderFinishedSemaphore)

            val imageAvailableSemaphore = device.createSemaphore(semaphoreCreateInfo.ptr(), null).getOrThrow()
            val pImageAvailableSemaphore = VkSemaphore.valueOf(imageAvailableSemaphore)

            val inFlightFence = device.createFence(fenceCreateInfo.ptr(), null).getOrThrow()

            val fences = VkFence.valueOf(inFlightFence)

            context(f: MemoryStack.Frame)
            fun render() {
                device.waitForFences(1u, fences.ptr(), VK_TRUE, ULong.MAX_VALUE)
                device.resetFences(1u, fences.ptr())

                val pImageIndex = NUInt32.malloc()
                device.acquireNextImageKHR(
                    swapchain,
                    ULong.MAX_VALUE,
                    imageAvailableSemaphore,
                    VkFence.fromNativeData(device, 0L),
                    pImageIndex.ptr()
                )

                commandBuffer.resetCommandBuffer(VkCommandBufferResetFlags.NONE)

                val imageIndex = pImageIndex.value.toInt()
                val beginInfo = VkCommandBufferBeginInfo.allocate().apply {}
                commandBuffer.beginCommandBuffer(beginInfo.ptr())

                val renderPassInfo = VkRenderPassBeginInfo.allocate().apply {
                    this.renderPass = renderPass
                    this.framebuffer = swapchainFramebuffers[imageIndex]
                    renderArea.offset.apply {
                        x = 0
                        y = 0
                    }
                    renderArea.extent = swapchainExtent
                    clearValueCount = 1u
                    pClearValues = clearColor.ptr()
                }

                commandBuffer.cmdBeginRenderPass(renderPassInfo.ptr(), VkSubpassContents.INLINE)

                commandBuffer.cmdBindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)
                commandBuffer.cmdDraw(3u, 1u, 0u, 0u)

                commandBuffer.cmdEndRenderPass()
                commandBuffer.endCommandBuffer()

                val waitStages = VkPipelineStageFlags.malloc(1).apply {
                    this[0] = VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT
                }
                val submitInfo = VkSubmitInfo.allocate().apply {
                    waitSemaphoreCount = 1u
                    pWaitSemaphores = pImageAvailableSemaphore.ptr()

                    pWaitDstStageMask = waitStages.ptr()

                    commandBufferCount = 1u
                    pCommandBuffers = pCommandBuffer.ptr()

                    signalSemaphoreCount = 1u
                    pSignalSemaphores = pRenderFinishedSemaphore.ptr()
                }
                graphicsQueue.queueSubmit(
                    1u,
                    submitInfo.ptr(),
                    inFlightFence
                )

                val presentInfo = VkPresentInfoKHR.allocate().apply {
                    waitSemaphoreCount = 1u
                    pWaitSemaphores = pRenderFinishedSemaphore.ptr()

                    swapchainCount = 1u
                    pSwapchains = VkSwapchainKHR.valueOf(swapchain).ptr()

                    pImageIndices = pImageIndex.ptr()
                }
                graphicsQueue.queuePresentKHR(presentInfo.ptr())
            }

            fun destroy() {
                device.destroySemaphore(renderFinishedSemaphore, null)
                device.destroyFence(inFlightFence, null)
            }
        }

        val frames = List(10) {
            Frame()
        }

        var frameIndex = 0
        val counter = AverageCounter(1000, 8)

        while (glfwWindowShouldClose(window) == GLFW_FALSE) {
            glfwPollEvents()
            MemoryStack {
                frames[frameIndex].render()
                frameIndex = (frameIndex + 1) % frames.size
                counter.invoke {
                    println("FPS: ${counter.averageCPS}")
                }
            }
        }

        device.deviceWaitIdle()

        frames.forEach {
            it.destroy()
        }
        device.destroyCommandPool(commandPool, null)
        for (framebuffer in swapchainFramebuffers) {
            device.destroyFramebuffer(framebuffer, null)
        }
        device.destroyPipeline(pipeline, null)
        device.destroyPipelineLayout(pipelineLayout, null)
        device.destroyRenderPass(renderPass, null)
        device.destroyShaderModule(vkTestShaderModule, null)
        for (imageView in swapchainImageViews) {
            device.destroyImageView(imageView, null)
        }
        device.destroySwapchainKHR(swapchain, null)
        device.destroyDevice(null)
        instance.destroySurfaceKHR(surface, null)
        if (debugUtilsMessenger != null) {
            instance.destroyDebugUtilsMessengerEXT(debugUtilsMessenger, null)
        }
        instance.destroyInstance(null)

        glfwTerminate()
    }
}

private fun populateDebugMessengerCreateInfo(debugCreateInfo: NValue<VkDebugUtilsMessengerCreateInfoEXT>) {
    debugCreateInfo.messageSeverity = VkDebugUtilsMessageSeverityFlagsEXT.VERBOSE_EXT +
        VkDebugUtilsMessageSeverityFlagsEXT.WARNING_EXT +
        VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT

    debugCreateInfo.messageType = VkDebugUtilsMessageTypeFlagsEXT.GENERAL_EXT +
        VkDebugUtilsMessageTypeFlagsEXT.VALIDATION_EXT +
        VkDebugUtilsMessageTypeFlagsEXT.PERFORMANCE_EXT

    debugCreateInfo.pfnUserCallback { messageSeverity, messageType, pCallbackData, pUserData ->
        if (VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT in messageSeverity) {
            System.err.println("Validation layer: " + pCallbackData.pMessage.string)
        } else {
            println("Validation layer: " + pCallbackData.pMessage.string)
        }
        VK_FALSE
    }
}

private fun MemoryStack.Frame.choosePhysicalDevice(instance: VkInstance): VkPhysicalDevice = MemoryStack {
    enumerate(instance::enumeratePhysicalDevices) { pointer, index ->
        VkPhysicalDevice.fromNativeData(
            instance,
            pointer[index]
        )
    }.asSequence()
        .map {
            val property = VkPhysicalDeviceProperties.allocate()
            it.getPhysicalDeviceProperties(property.ptr())
            it to property
        }
        .maxWithOrNull(
            compareBy<Pair<VkPhysicalDevice, NValue<VkPhysicalDeviceProperties>>> { (_, property) ->
                when (property.deviceType) {
                    VkPhysicalDeviceType.DISCRETE_GPU -> 4
                    VkPhysicalDeviceType.VIRTUAL_GPU -> 3
                    VkPhysicalDeviceType.INTEGRATED_GPU -> 2
                    VkPhysicalDeviceType.CPU -> 1
                    VkPhysicalDeviceType.OTHER -> 0
                }
            }.thenBy { (_, property) ->
                when (property.vendorID) {
                    0x10DEU -> 4 // NVIDIA
                    0x1002U -> 3 // AMD
                    0x8086U -> 2 // Intel
                    else -> 1
                }
            }
        )?.first ?: error("No suitable physical device found.")
}
