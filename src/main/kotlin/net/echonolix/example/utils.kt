package net.echonolix.example

import net.echonolix.caelum.*
import net.echonolix.caelum.glfw.functions.glfwGetWindowSize
import net.echonolix.caelum.glfw.structs.GLFWWindow
import net.echonolix.caelum.vulkan.*
import net.echonolix.caelum.vulkan.enums.VkFormat
import net.echonolix.caelum.vulkan.enums.VkPhysicalDeviceType
import net.echonolix.caelum.vulkan.enums.VkPresentModeKHR
import net.echonolix.caelum.vulkan.enums.get
import net.echonolix.caelum.vulkan.flags.VkDebugUtilsMessageSeverityFlagsEXT
import net.echonolix.caelum.vulkan.flags.VkDebugUtilsMessageTypeFlagsEXT
import net.echonolix.caelum.vulkan.flags.VkQueueFlags
import net.echonolix.caelum.vulkan.handles.*
import net.echonolix.caelum.vulkan.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString


fun loadLibrary(name: String) {
    System.load(Path("$name.dll").absolutePathString())
}

data class SwapchainSupportDetails(
    val capabilities: NValue<VkSurfaceCapabilitiesKHR>,
    val formats: List<NPointer<VkSurfaceFormatKHR>>,
    val presentModes: List<VkPresentModeKHR>
)

context(_: MemoryStack)
fun VkPhysicalDevice.querySwapchainSupport(surface: VkSurfaceKHR): SwapchainSupportDetails {
    val capabilities = VkSurfaceCapabilitiesKHR.allocate()
    getPhysicalDeviceSurfaceCapabilitiesKHR(surface, capabilities.ptr())

    val formatCount = NUInt32.malloc()
    getPhysicalDeviceSurfaceFormatsKHR(surface, formatCount.ptr(), null)
    val formatsBuffer = VkSurfaceFormatKHR.allocate(formatCount.value.toLong())
    getPhysicalDeviceSurfaceFormatsKHR(surface, formatCount.ptr(), formatsBuffer.ptr())
    val formats = buildList {
        repeat(formatCount.value.toInt()) {
            add(formatsBuffer[it.toLong()])
        }
    }

    val presentModeCount = NUInt32.malloc()
    getPhysicalDeviceSurfacePresentModesKHR(surface, presentModeCount.ptr(), null)
    val presentModesBuffer = VkPresentModeKHR.malloc(presentModeCount.value.toLong())
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

context(_: MemoryStack)
fun chooseSwapchainExtent(
    window: NPointer<GLFWWindow>,
    capabilities: NValue<VkSurfaceCapabilitiesKHR>
): NPointer<VkExtent2D> {
    return if (capabilities.currentExtent.width != UInt.MAX_VALUE) {
        capabilities.currentExtent
    } else {
        val widthBuffer = NInt.malloc()
        val heightBuffer = NInt.malloc()
        glfwGetWindowSize(window, widthBuffer.ptr(), heightBuffer.ptr())

        val actualExtent = VkExtent2D.allocate {
            this.width = widthBuffer.value.toUInt()
                .coerceIn(capabilities.minImageExtent.width, capabilities.maxImageExtent.width)
            this.height = heightBuffer.value.toUInt()
                .coerceIn(capabilities.minImageExtent.height, capabilities.maxImageExtent.height)
        }

        actualExtent.ptr()
    }
}

fun populateDebugMessengerCreateInfo(debugCreateInfo: NValue<VkDebugUtilsMessengerCreateInfoEXT>) {
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

fun MemoryStack.choosePhysicalDevice(instance: VkInstance): VkPhysicalDevice = MemoryStack {
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

context(_: MemoryStack)
@OptIn(UnsafeAPI::class)
fun VkDevice.makeShaderModule(code: ByteArray): VkShaderModule {
    MemoryStack {
        val codeBuffer = NInt8.malloc(code.size.toLong())
        code.copyTo(codeBuffer.ptr())
        val createInfo = VkShaderModuleCreateInfo.allocate {
            codeSize = code.size.toLong()
            pCode = reinterpret_cast(codeBuffer.ptr())
        }
        return createShaderModule(createInfo.ptr(), null).getOrThrow()
    }
}

context(_: MemoryStack)
fun VkPhysicalDevice.chooseGraphicsQueue(surface: VkSurfaceKHR): Int {
    var graphicsQueueFamilyIndex = -1
    MemoryStack {
        val queueFamilyPropertyCount = NUInt32.calloc()
        this@chooseGraphicsQueue.getPhysicalDeviceQueueFamilyProperties(queueFamilyPropertyCount.ptr(), null)
        val queueFamilyProperties = VkQueueFamilyProperties.allocate(queueFamilyPropertyCount.value.toLong())
        this@chooseGraphicsQueue.getPhysicalDeviceQueueFamilyProperties(
            queueFamilyPropertyCount.ptr(),
            queueFamilyProperties.ptr()
        )
        val isPresentSupported = NUInt32.malloc()
        repeat(queueFamilyPropertyCount.value.toInt()) {
            val queueFamilyProperty = queueFamilyProperties[it.toLong()]
            if (graphicsQueueFamilyIndex == -1 && queueFamilyProperty.queueFlags.contains(VkQueueFlags.Companion.GRAPHICS)) {
                this@chooseGraphicsQueue.getPhysicalDeviceSurfaceSupportKHR(it.toUInt(), surface, isPresentSupported.ptr())
                check(isPresentSupported.value == 1u) { "Graphics queue family does not support present." }
                graphicsQueueFamilyIndex = it
            }
        }
    }
    return graphicsQueueFamilyIndex
}