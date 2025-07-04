package net.echonolix.example

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
import net.echonolix.caelum.vulkan.unions.*
import net.echonolix.example.utils.AverageCounter
import java.lang.foreign.Arena

class VkTestS

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

        val appInfo = VkApplicationInfo.allocate {
            pApplicationName = "Hello Vulkan".c_str()
            applicationVersion = VkApiVersion(0u, 1u, 0u, 0u).value
            pEngineName = "VK Test".c_str()
            engineVersion = VkApiVersion(0u, 1u, 0u, 0u).value
            apiVersion = VK_API_VERSION_1_0.value
        }

        val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate()
        populateDebugMessengerCreateInfo(debugCreateInfo)

        val createInfo = VkInstanceCreateInfo.allocate {
            pApplicationInfo = appInfo.ptr()
            enabledExtensions(extensions.c_strs())
            enabledLayers(layers.c_strs())
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
        val graphicsQueueFamilyIndex = physicalDevice.chooseGraphicsQueue(surface)

        println("Queue Family: $graphicsQueueFamilyIndex")

        val queuePriority = NFloat.arrayOf(1.0f)
        val queueCreateInfos = VkDeviceQueueCreateInfo.allocate(1)
        queueCreateInfos[0].apply {
            queueFamilyIndex = graphicsQueueFamilyIndex.toUInt()
            queues(queuePriority)
        }
        val deviceExtensions = setOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
        val deviceCreateInfo = VkDeviceCreateInfo.allocate {
            queueCreateInfoes(queueCreateInfos)

            pEnabledFeatures = physicalDeviceFeatures.ptr()

            enabledExtensions(deviceExtensions.c_strs())
            enabledLayers(layers.c_strs())
        }
        val device = physicalDevice.createDevice(deviceCreateInfo.ptr(), null).getOrThrow()
        val graphicsQueueV = VkQueue.malloc()
        device.getDeviceQueue(graphicsQueueFamilyIndex.toUInt(), 0u, graphicsQueueV.ptr())
        val graphicsQueue = VkQueue.fromNativeData(device, graphicsQueueV.value)

        val swapchainSupport = physicalDevice.querySwapchainSupport(surface)
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
        val swapchainExtent = chooseSwapchainExtent(window, swapchainSupport.capabilities)

        val swapchainCreateInfo = VkSwapchainCreateInfoKHR.allocate {
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
            val buffer = VkImage.malloc(swapchainImageCount.value.toLong())
            device.getSwapchainImagesKHR(swapchain, swapchainImageCount.ptr(), buffer.ptr())
            repeat(swapchainImageCount.value.toInt()) {
                add(VkImage.fromNativeData(device, buffer[it]))
            }
        }
        val swapchainImageFormat = surfaceFormat.format

        val swapchainImageViews = buildList {
            repeat(swapchainImageCount.value.toInt()) {
                val createInfo = VkImageViewCreateInfo.allocate {
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

        val vktestSpvData = VkTestS::class.java.getResource("/vktest.spv")!!.readBytes()
        val vkTestShaderModule = device.makeShaderModule(vktestSpvData)

        val (pipelineLayout, renderPass, pipeline) = makePipeline(
            vkTestShaderModule,
            swapchainExtent,
            device,
            swapchainImageFormat
        )

        val swapchainFramebuffers = buildList {
            repeat(swapchainImageViews.size) {
                val framebufferCreateInfo = VkFramebufferCreateInfo.allocate {
                    this.renderPass = renderPass
                    attachments(VkImageView.arrayOf(swapchainImageViews[it]))
                    this.width = swapchainExtent.width
                    this.height = swapchainExtent.height
                    this.layers = 1u
                }
                add(device.createFramebuffer(framebufferCreateInfo.ptr(), null).getOrThrow())
            }
        }

        val commandPoolCreateInfo = VkCommandPoolCreateInfo.allocate {
            flags = VkCommandPoolCreateFlags.RESET_COMMAND_BUFFER
            queueFamilyIndex = graphicsQueueFamilyIndex.toUInt()
        }

        val commandPool = device.createCommandPool(commandPoolCreateInfo.ptr(), null).getOrThrow()

        val commandBufferAllocateInfo = VkCommandBufferAllocateInfo.allocate {
            this.commandPool = commandPool
            level = VkCommandBufferLevel.PRIMARY
            commandBufferCount = 1u
        }
        val semaphoreCreateInfo = VkSemaphoreCreateInfo.allocate {}

        val fenceCreateInfo = VkFenceCreateInfo.allocate {
            flags = VkFenceCreateFlags.SIGNALED
        }

        val clearColor = VkClearValue.malloc(1)
        clearColor[0].apply {
            color.float32[0] = 0f
            color.float32[1] = 0f
            color.float32[2] = 0f
            color.float32[3] = 1f
        }

        class Frame {
            val pCommandBuffer = VkCommandBuffer.malloc(1)
            val commandBuffer: VkCommandBuffer

            init {
                device.allocateCommandBuffers(commandBufferAllocateInfo.ptr(), pCommandBuffer.ptr())
                commandBuffer = VkCommandBuffer.fromNativeData(commandPool, pCommandBuffer[0])
            }

            val renderFinishedSemaphore = device.createSemaphore(semaphoreCreateInfo.ptr(), null).getOrThrow()
            val pRenderFinishedSemaphore = VkSemaphore.arrayOf(renderFinishedSemaphore)

            val imageAvailableSemaphore = device.createSemaphore(semaphoreCreateInfo.ptr(), null).getOrThrow()
            val pImageAvailableSemaphore = VkSemaphore.arrayOf(imageAvailableSemaphore)

            val inFlightFence = device.createFence(fenceCreateInfo.ptr(), null).getOrThrow()

            val fences = VkFence.arrayOf(inFlightFence)

            context(_: MemoryStack)
            fun render() {
                device.waitForFences(1u, fences.ptr(), VK_TRUE, ULong.MAX_VALUE)
                device.resetFences(1u, fences.ptr())

                val pImageIndex = NUInt32.malloc(1)
                device.acquireNextImageKHR(
                    swapchain,
                    ULong.MAX_VALUE,
                    imageAvailableSemaphore,
                    VkFence.fromNativeData(device, 0L),
                    pImageIndex.ptr()
                )

                commandBuffer.resetCommandBuffer(VkCommandBufferResetFlags.NONE)

                val imageIndex = pImageIndex[0].toInt()
                val beginInfo = VkCommandBufferBeginInfo.allocate {}
                commandBuffer.beginCommandBuffer(beginInfo.ptr())

                val renderPassInfo = VkRenderPassBeginInfo.allocate {
                    this.renderPass = renderPass
                    this.framebuffer = swapchainFramebuffers[imageIndex]
                    renderArea.offset {
                        x = 0
                        y = 0
                    }
                    renderArea.extent = swapchainExtent
                    clearValues(clearColor)
                }

                commandBuffer.cmdBeginRenderPass(renderPassInfo.ptr(), VkSubpassContents.INLINE)

                commandBuffer.cmdBindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)
                commandBuffer.cmdDraw(3u, 1u, 0u, 0u)

                commandBuffer.cmdEndRenderPass()
                commandBuffer.endCommandBuffer()

                val submitInfo = VkSubmitInfo.allocate {
                    waitSemaphores(pImageAvailableSemaphore, VkPipelineStageFlags.arrayOf(VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT))

                    commandBuffers(static_cast(pCommandBuffer))

                    signalSemaphores(pRenderFinishedSemaphore)
                }
                graphicsQueue.queueSubmit(
                    1u,
                    submitInfo.ptr(),
                    inFlightFence
                )

                val presentInfo = VkPresentInfoKHR.allocate {
                    waitSemaphores(pRenderFinishedSemaphore)

                    val dummy = VkResult.malloc(1)
                    swapchains(VkSwapchainKHR.arrayOf(swapchain), pImageIndex, dummy)
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

context(_: MemoryStack)
@OptIn(UnsafeAPI::class)
private fun makePipeline(
    vkTestShaderModule: VkShaderModule,
    swapchainExtent: NPointer<VkExtent2D>,
    device: VkDevice,
    swapchainImageFormat: VkFormat
): Triple<VkPipelineLayout, VkRenderPass, VkPipeline> {
    MemoryStack {
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

        val vertexInputStateCreateInfo = VkPipelineVertexInputStateCreateInfo.allocate {
            vertexBindingDescriptionCount = 0u
            pVertexBindingDescriptions = nullptr()
            vertexAttributeDescriptionCount = 0u
            pVertexAttributeDescriptions = nullptr()
        }

        val inputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.allocate {
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

        val viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.allocate {
            viewportCount = 1u
            pViewports = viewport.ptr()
            scissorCount = 1u
            pScissors = scissor.ptr()
        }

        val rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.allocate {
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

        val multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.allocate {
            sampleShadingEnable = VK_FALSE
            rasterizationSamples = VkSampleCountFlags.`1_BIT`
            minSampleShading = 1.0f // Optional
            pSampleMask = nullptr() // Optional
            alphaToCoverageEnable = VK_FALSE // Optional
            alphaToOneEnable = VK_FALSE // Optional
        }

        val colorBlendAttachmentState = VkPipelineColorBlendAttachmentState.allocate {
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

        val colorBlendState = VkPipelineColorBlendStateCreateInfo.allocate {
            logicOpEnable = VK_FALSE
            logicOp = VkLogicOp.COPY // Optional
            attachmentCount = 1u
            pAttachments = colorBlendAttachmentState.ptr()
            blendConstants[0] = 0.0f // Optional
            blendConstants[1] = 0.0f // Optional
            blendConstants[2] = 0.0f // Optional
            blendConstants[3] = 0.0f // Optional
        }

        val pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.allocate {
            setLayoutCount = 0u // Optional
            pSetLayouts = nullptr() // Optional
            pushConstantRangeCount = 0u // Optional
            pPushConstantRanges = nullptr() // Optional
        }

        val pipelineLayout = device.createPipelineLayout(pipelineLayoutCreateInfo.ptr(), null).getOrThrow()

        val colorAttachment = VkAttachmentDescription.allocate {
            format = swapchainImageFormat
            samples = VkSampleCountFlags.`1_BIT`
            loadOp = VkAttachmentLoadOp.CLEAR
            storeOp = VkAttachmentStoreOp.STORE
            stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
            stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
            initialLayout = VkImageLayout.UNDEFINED
            finalLayout = VkImageLayout.PRESENT_SRC_KHR
        }

        val colorAttachmentRef = VkAttachmentReference.allocate {
            attachment = 0u
            layout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
        }

        val subpass = VkSubpassDescription.allocate {
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
            colorAttachmentCount = 1u
            pColorAttachments = colorAttachmentRef.ptr()
        }

        val subpassDependency = VkSubpassDependency.allocate {
            srcSubpass = VK_SUBPASS_EXTERNAL
            dstSubpass = 0u
            srcStageMask = VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT
            srcAccessMask = VkAccessFlags.NONE
            dstStageMask = VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT
            dstAccessMask = VkAccessFlags.COLOR_ATTACHMENT_WRITE
        }

        val renderPassCreateInfo = VkRenderPassCreateInfo.allocate {
            attachmentCount = 1u
            pAttachments = colorAttachment.ptr()
            subpassCount = 1u
            pSubpasses = subpass.ptr()
            dependencyCount = 1u
            pDependencies = subpassDependency.ptr()
        }

        val renderPass = device.createRenderPass(renderPassCreateInfo.ptr(), null).getOrThrow()

        val pipelineCreateInfo = VkGraphicsPipelineCreateInfo.allocate {
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
        return Triple(pipelineLayout, renderPass, pipeline)
    }
}

