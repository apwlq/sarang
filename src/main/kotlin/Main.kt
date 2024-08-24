import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

fun receiveMulticast(group: InetAddress, port: Int, bufferSize: Int): ByteArray? {
    val socket = MulticastSocket(port)
    socket.joinGroup(group)

    val receivedData = mutableListOf<ByteArray>()
    var totalSize = 0

    try {
        while (true) {
            val buffer = ByteArray(bufferSize)
            val packet = DatagramPacket(buffer, buffer.size)

            socket.receive(packet)
            println("Received packet of size ${packet.length} bytes")

            val packetData = packet.data.copyOf(packet.length)
            receivedData.add(packetData)
            totalSize += packet.length

            // 패킷 크기가 버퍼 크기보다 작으면 전송이 완료되었다고 가정
            if (packet.length < bufferSize) {
                break
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    } finally {
        socket.leaveGroup(group)
        socket.close()
    }

    // 모든 수신 데이터를 하나의 바이트 배열로 결합
    val combinedData = ByteArray(totalSize)
    var offset = 0
    for (chunk in receivedData) {
        System.arraycopy(chunk, 0, combinedData, offset, chunk.size)
        offset += chunk.size
    }
    return combinedData
}

fun byteArrayToImage(data: ByteArray?): BufferedImage? {
    if (data == null) return null
    return try {
        ByteArrayInputStream(data).use { bais ->
            ImageIO.read(bais)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun scaleImageToWidth(image: BufferedImage, targetWidth: Int): Image {
    val aspectRatio = image.height.toDouble() / image.width.toDouble()
    val targetHeight = (targetWidth * aspectRatio).toInt()
    return image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
}

fun updateImage(label: JLabel, image: BufferedImage, frameWidth: Int) {
    // 이미지를 가로 크기에 맞게 스케일링
    val scaledImage = scaleImageToWidth(image, frameWidth)
    label.icon = ImageIcon(scaledImage)
}

fun main() {
    val multicastGroup = InetAddress.getByName("230.0.0.0")
    val port = 4446
    val bufferSize = 65000

    println("Waiting to receive image data...")

    // GUI 초기화
    val frame = JFrame("sarang")
    val label = JLabel()
    frame.contentPane.add(label)

//    // 앱 아이콘 설정
//    val classLoader = Thread.currentThread().contextClassLoader
//    val iconStream = classLoader.getResourceAsStream("icon.jpg")
//    val iconImage = ImageIO.read(iconStream)
//    frame.iconImage = iconImage

    // 전체 화면 모드로 설정
    val gd: GraphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    frame.isUndecorated = true
    gd.fullScreenWindow = frame

    val screenWidth = frame.width  // 전체 화면의 너비를 가져옴

    while (true) {
        // 멀티캐스트로 수신된 데이터를 조합
        val receivedData = receiveMulticast(multicastGroup, port, bufferSize)

        // 바이트 배열을 이미지로 변환
        val image = byteArrayToImage(receivedData)

        if (image != null) {
            println("Image received successfully!")
            // 받은 이미지를 가로 크기에 맞게 업데이트
            updateImage(label, image, screenWidth)
            frame.repaint()  // 전체 화면에서 이미지 업데이트를 반영
        } else {
            println("Failed to reconstruct image.")
        }
    }
}
