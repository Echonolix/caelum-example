package net.echonolix.example

import net.echonolix.caelum.*
import net.echonolix.caelum.glfw.consts.GLFW_CLIENT_API
import net.echonolix.caelum.glfw.consts.GLFW_FALSE
import net.echonolix.caelum.glfw.consts.GLFW_NO_API
import net.echonolix.caelum.glfw.consts.GLFW_RESIZABLE
import net.echonolix.caelum.glfw.consts.GLFW_VISIBLE
import net.echonolix.caelum.glfw.functions.*
import net.echonolix.caelum.vulkan.*
import net.echonolix.caelum.vulkan.Vk
import net.echonolix.caelum.vulkan.flags.VkDebugUtilsMessageSeverityFlagsEXT
import net.echonolix.caelum.vulkan.flags.VkDebugUtilsMessageTypeFlagsEXT
import net.echonolix.caelum.vulkan.handles.VkPhysicalDevice
import net.echonolix.caelum.vulkan.handles.get
import net.echonolix.caelum.vulkan.structs.*
import org.openjdk.jmh.infra.Blackhole

fun caelumCreateVkInstance(blackhole: Blackhole) = MemoryStack {
    val layers = setOf("VK_LAYER_KHRONOS_validation")
    val extensions = setOf("VK_KHR_surface", "VK_KHR_win32_surface", "VK_EXT_debug_utils")

    val appInfo = VkApplicationInfo.allocate {
        pApplicationName = "Hello Vulkan".c_str()
        applicationVersion = VkApiVersion(0u, 1u, 0u, 0u).value
        pEngineName = "VK Test".c_str()
        engineVersion = VkApiVersion(0u, 1u, 0u, 0u).value
        apiVersion = VK_API_VERSION_1_0.value
    }

    val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate {
        messageSeverity = VkDebugUtilsMessageSeverityFlagsEXT.VERBOSE_EXT +
            VkDebugUtilsMessageSeverityFlagsEXT.WARNING_EXT +
            VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT

        messageType = VkDebugUtilsMessageTypeFlagsEXT.GENERAL_EXT +
            VkDebugUtilsMessageTypeFlagsEXT.VALIDATION_EXT +
            VkDebugUtilsMessageTypeFlagsEXT.PERFORMANCE_EXT

        pfnUserCallback { messageSeverity, messageType, pCallbackData, pUserData ->
            if (VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT in messageSeverity) {
//                System.err.println("Validation layer: " + pCallbackData.pMessage.string)
                blackhole.consume(pCallbackData.pMessage.string)
            } else {
//                println("Validation layer: " + pCallbackData.pMessage.string)
                blackhole.consume(pCallbackData.pMessage.string)
            }
            VK_FALSE
        }
    }

    val createInfo = VkInstanceCreateInfo.allocate {
        pApplicationInfo = appInfo.ptr()
        enabledExtensions(extensions.c_strs())
        enabledLayers(layers.c_strs())
        pNext = debugCreateInfo.ptr()
    }

    val instance = Vk.createInstance(createInfo.ptr(), null).getOrThrow()

    instance.destroyInstance(null)
}

fun caelumCreateVkDevice(blackhole: Blackhole) = MemoryStack {
    glfwInit()
    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    val width = 800
    val height = 600
    val window = glfwCreateWindow(width, height, "Vulkan".c_str(), nullptr(), nullptr())

    val layers = setOf("VK_LAYER_KHRONOS_validation")
    val extensions = setOf("VK_KHR_surface", "VK_KHR_win32_surface", "VK_EXT_debug_utils")

    val appInfo = VkApplicationInfo.allocate {
        pApplicationName = "Hello Vulkan".c_str()
        applicationVersion = VkApiVersion(0u, 1u, 0u, 0u).value
        pEngineName = "VK Test".c_str()
        engineVersion = VkApiVersion(0u, 1u, 0u, 0u).value
        apiVersion = VK_API_VERSION_1_0.value
    }

    val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate {
        messageSeverity = VkDebugUtilsMessageSeverityFlagsEXT.VERBOSE_EXT +
            VkDebugUtilsMessageSeverityFlagsEXT.WARNING_EXT +
            VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT

        messageType = VkDebugUtilsMessageTypeFlagsEXT.GENERAL_EXT +
            VkDebugUtilsMessageTypeFlagsEXT.VALIDATION_EXT +
            VkDebugUtilsMessageTypeFlagsEXT.PERFORMANCE_EXT

        pfnUserCallback { messageSeverity, messageType, pCallbackData, pUserData ->
            if (VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT in messageSeverity) {
//                System.err.println("Validation layer: " + pCallbackData.pMessage.string)
                blackhole.consume(pCallbackData.pMessage.string)
            } else {
//                println("Validation layer: " + pCallbackData.pMessage.string)
                blackhole.consume(pCallbackData.pMessage.string)
            }
            VK_FALSE
        }
    }

    val createInfo = VkInstanceCreateInfo.allocate {
        pApplicationInfo = appInfo.ptr()
        enabledExtensions(extensions.c_strs())
        enabledLayers(layers.c_strs())
        pNext = debugCreateInfo.ptr()
    }

    val instance = Vk.createInstance(createInfo.ptr(), null).getOrThrow()

    val debugUtilsMessenger = instance.createDebugUtilsMessengerEXT(debugCreateInfo.ptr(), null).getOrThrow()

    val count = NUInt32.valueOf(1u)
    val pPhysicalDevices = VkPhysicalDevice.malloc(1)
    instance.enumeratePhysicalDevices(count.ptr(), pPhysicalDevices.ptr()).getOrThrow()
    val physicalDevice = VkPhysicalDevice.fromNativeData(instance, pPhysicalDevices[0])

    val physicalDeviceProperties = VkPhysicalDeviceProperties.allocate()
    val physicalDeviceFeatures = VkPhysicalDeviceFeatures.allocate()
    physicalDevice.getPhysicalDeviceProperties(physicalDeviceProperties.ptr())
    physicalDevice.getPhysicalDeviceFeatures(physicalDeviceFeatures.ptr())

    val surface = glfwCreateWindowSurface(instance, window, null).getOrThrow()
    val graphicsQueueFamilyIndex = 0

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

    device.destroyDevice(null)
    instance.destroySurfaceKHR(surface, null)
    instance.destroyDebugUtilsMessengerEXT(debugUtilsMessenger, null)
    instance.destroyInstance(null)

    glfwDestroyWindow(window)
    glfwTerminate()
}