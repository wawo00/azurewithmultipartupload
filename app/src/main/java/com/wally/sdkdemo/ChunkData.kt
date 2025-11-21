package com.wally.sdkdemo

/**
 * 分块数据类 - 用于多部分文件上传的数据块
 *
 * 该数据类表示文件分块上传中的单个数据块，包含了数据内容、位置信息、
 * 块索引等上传所需的完整信息。
 *
 * @param data 数据块的字节数组内容，可为空
 * @param offset 该数据块在原始文件中的偏移位置（字节）
 * @param chunkIndex 数据块的序号索引（从0开始）
 * @param fileName 原始文件名
 * @param isLastChunk 是否为最后一个数据块的标识
 * @param timestamp 数据块创建的时间戳，默认为当前系统时间
 */
data class ChunkData @JvmOverloads constructor(
    var data: ByteArray?,                                    // 数据块内容
    var offset: Long,                                        // 文件偏移量
    var chunkIndex: Int,                                     // 块序号
    var fileName: String="",                                    // 文件名
    var isLastChunk: Boolean=false,                               // 是否最后一块
    var timestamp: Long = System.currentTimeMillis()        // 创建时间戳
) {

    /**
     * 计算属性：数据块大小
     * 返回数据块的字节数，如果数据为空则返回0
     */
    val size: Int
        get() = data?.size ?: 0

    /**
     * 自定义字符串表示
     * 避免打印字节数组内容，只显示关键信息以便调试
     *
     * @return 格式化的字符串，包含偏移量、块索引、文件名、是否最后一块和大小
     */
    override fun toString(): String {
        return "ChunkData(" +
                "offset=$offset, " +
                "chunkIndex=$chunkIndex, " +
                "fileName='$fileName', " +
                "isLastChunk=$isLastChunk, " +
                "size=$size" +
                ")"
    }

}
