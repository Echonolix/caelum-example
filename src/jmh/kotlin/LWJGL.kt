package net.echonolix.example

import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.VK10.*
import org.openjdk.jmh.infra.Blackhole

private fun MemoryStack.asPointerBuffer(collection: Collection<String>): PointerBuffer {
    val buffer = mallocPointer(collection.size)
    collection.forEach { buffer.put(UTF8(it)) }
    return buffer.rewind()
}

fun lwjglCreateVkInstance(blackhole: Blackhole) = MemoryStack.stackPush().use { stack ->
    val layers = setOf("VK_LAYER_KHRONOS_validation")
    val extensions = setOf("VK_KHR_surface", "VK_KHR_win32_surface", "VK_EXT_debug_utils")

    val appInfo = VkApplicationInfo.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
        .pApplicationName(stack.UTF8Safe("Hello Vulkan"))
        .applicationVersion(VK_API_VERSION_1_0)
        .pEngineName(stack.UTF8Safe("VK Test"))
        .engineVersion(VK_API_VERSION_1_0)
        .apiVersion(VK_API_VERSION_1_0)

    val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
        .messageSeverity(
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
        )
        .messageType(
            VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
        )
        .pfnUserCallback { messageSeverity, messageType, pCallbackData, pUserData ->
            val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
            if (VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT and messageSeverity != 0) {
//                System.err.println("Validation layer: " + callbackData.pMessageString())
                blackhole.consume(callbackData.pMessageString())
            } else {
//                println("Validation layer: " + callbackData.pMessageString())
                blackhole.consume(callbackData.pMessageString())
            }
            VK_FALSE
        }

    val createInfo = VkInstanceCreateInfo.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
        .pApplicationInfo(appInfo)
        .ppEnabledExtensionNames(stack.asPointerBuffer(extensions))
        .ppEnabledLayerNames(stack.asPointerBuffer(layers))
        .pNext(debugCreateInfo)

    val pInstance = stack.mallocPointer(1)
    if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
        throw RuntimeException("Failed to create instance")
    }
    VkInstance(pInstance.get(0), createInfo)
}

fun lwjglCreateVkDevice(blackhole: Blackhole) = MemoryStack.stackPush().use { stack ->
    glfwInit()
    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    val width = 800
    val height = 600
    val window = glfwCreateWindow(width, height, "Vulkan", 0L, 0L)

    val layers = setOf("VK_LAYER_KHRONOS_validation")
    val extensions = setOf("VK_KHR_surface", "VK_KHR_win32_surface", "VK_EXT_debug_utils")

    val appInfo = VkApplicationInfo.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
        .pApplicationName(stack.UTF8Safe("Hello Vulkan"))
        .applicationVersion(VK_API_VERSION_1_0)
        .pEngineName(stack.UTF8Safe("VK Test"))
        .engineVersion(VK_API_VERSION_1_0)
        .apiVersion(VK_API_VERSION_1_0)

    val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
        .messageSeverity(
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
        )
        .messageType(
            VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
        )
        .pfnUserCallback { messageSeverity, messageType, pCallbackData, pUserData ->
            val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
            if (VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT and messageSeverity != 0) {
//                System.err.println("Validation layer: " + callbackData.pMessageString())
                blackhole.consume(callbackData.pMessageString())
            } else {
//                println("Validation layer: " + callbackData.pMessageString())
                blackhole.consume(callbackData.pMessageString())
            }
            VK_FALSE
        }

    val createInfo = VkInstanceCreateInfo.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
        .pApplicationInfo(appInfo)
        .ppEnabledExtensionNames(stack.asPointerBuffer(extensions))
        .ppEnabledLayerNames(stack.asPointerBuffer(layers))
        .pNext(debugCreateInfo)

    val pInstance = stack.mallocPointer(1)
    if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
        throw RuntimeException("Failed to create instance")
    }
    val instance = VkInstance(pInstance.get(0), createInfo)

    val pDebugUtilsMessenger = stack.mallocLong(1)
    vkCreateDebugUtilsMessengerEXT(instance, debugCreateInfo, null, pDebugUtilsMessenger)
    val debugUtilsMessenger = pDebugUtilsMessenger.get(0)

    val count = stack.ints(1)
    val pPhysicalDevices = stack.mallocPointer(1)
    vkEnumeratePhysicalDevices(instance, count, pPhysicalDevices)
    val physicalDevice = VkPhysicalDevice(pPhysicalDevices[0], instance)

    val physicalDeviceProperties = VkPhysicalDeviceProperties.calloc(stack)
    val physicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc(stack)
    vkGetPhysicalDeviceProperties(physicalDevice, physicalDeviceProperties)
    vkGetPhysicalDeviceFeatures(physicalDevice, physicalDeviceFeatures)

    val pSurface = stack.mallocLong(1)
    val v = glfwCreateWindowSurface(instance, window, null, pSurface)
    if (v != VK_SUCCESS) {
        println(v)
        throw RuntimeException("Failed to create window surface")
    }
    val surface = pSurface.get(0)

    val graphicsQueueFamilyIndex = 0

    val queuePriority = stack.floats(1.0f)
    val queueCreateInfos = VkDeviceQueueCreateInfo.calloc(1, stack)
    queueCreateInfos.apply(0) {
        it.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
        it.queueFamilyIndex(graphicsQueueFamilyIndex)
        it.pQueuePriorities(queuePriority)
    }

    val deviceExtensions = setOf("VK_KHR_swapchain")
    val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
        .pQueueCreateInfos(queueCreateInfos)
        .pEnabledFeatures(physicalDeviceFeatures)
        .ppEnabledExtensionNames(stack.asPointerBuffer(deviceExtensions))
        .ppEnabledLayerNames(stack.asPointerBuffer(layers))

    val pDevice = stack.mallocPointer(1)
    if (vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice) != VK_SUCCESS) {
        throw RuntimeException("Failed to create logical device")
    }
    val device = VkDevice(pDevice.get(0), physicalDevice, deviceCreateInfo)

    vkDestroyDevice(device, null)
    vkDestroySurfaceKHR(instance, surface, null)
    vkDestroyDebugUtilsMessengerEXT(instance, debugUtilsMessenger, null)
    vkDestroyInstance(instance, null)

    glfwDestroyWindow(window)
    glfwTerminate()
}