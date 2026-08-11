package com.otakup.niriko.plugin.kazumi

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Kazumi (Hive_ce) 二进制读取器。
 *
 * 依据 hive_ce 2.11.x 源码实现（lib/src/binary/）：
 * - 文件 = 有序 frame：`uint32 LE 帧长` + 帧内容（key + value）+ `uint32 LE CRC32`
 * - CRC 覆盖「帧长字段起、CRC 字段前的全部字节」（Dart Crc32 与 java.util.zip.CRC32 算法一致）
 * - key：1 字节 key 类型（0=uint32 LE、1=UTF8 字符串长度1字节）+ key 值
 * - value：1 字节 value 类型 + 负载（int/double 均按 float64 LE 存储；string 为 uint32 LE 长度 + UTF8；
 *   list/map 前置 uint32 LE 长度；对象 = typeId + 字段数 byte + 每字段 fieldId byte + value 递归）
 * - HiveField 对象：typeId 为自定义类型（0=BangumiItem、3=CollectedBangumi、4=BangumiTag），
 *   编码 = typeId 字节（<21 直接用；=21 时后续 2 字节扩展 ID）+ `numOfFields byte` + 逐字段 [fieldId byte, value]
 * 容错：单帧 CRC 失败/越界 → 跳过该帧继续；不解析的字段跳过字节，尽量拿到字段0-14。
 */
object KazumiHiveReader {

    // FrameValueType（hive_ce FrameValueType）
    private const val NULL_T = 0
    private const val INT_T = 1
    private const val DOUBLE_T = 2
    private const val BOOL_T = 3
    private const val STRING_T = 4
    private const val BYTE_LIST_T = 5
    private const val INT_LIST_T = 6
    private const val DOUBLE_LIST_T = 7
    private const val BOOL_LIST_T = 8
    private const val STRING_LIST_T = 9
    private const val LIST_T = 10
    private const val MAP_T = 11
    private const val HIVE_LIST_T = 12
    private const val INT_SET_T = 13
    private const val DOUBLE_SET_T = 14
    private const val STRING_SET_T = 15
    private const val DATE_TIME = 16
    private const val BIG_INT = 17
    private const val DATE_TIME_WITH_TZ = 18
    private const val SET_T = 19
    private const val DURATION = 20
    private const val TYPE_ID_EXTENSION = 21

    // Kazumi adapter typeIds
    // 注意：hive_ce 对自定义 adapter 写盘时统一做 typeId + reservedTypeIds(+32) 偏移
    // （type_registry_impl.dart: calculateTypeId(non-internal) => n + 32），
    // 因此 Kazumi 源码中的 0/3/4 在磁盘上是 32/35/36。实测 collectibles.tmp 第一帧
    // valueType=0x23(35)=CollectedBangumi、其字段0 valueType=0x20(32)=BangumiItem，完全吻合。
    private const val TYPE_BANGUMI_ITEM = 32
    private const val TYPE_COLLECTED_BANGUMI = 35
    private const val TYPE_BANGUMI_TAG = 36

    /**
     * 从 collectibles.hive 读取所有收藏条目。
     * @param bytes hive 文件字节
     * @return 解析出的条目（CRC 校验失败/无法解码的帧被跳过）
     */
    fun readCollectibles(bytes: ByteArray): List<KazumiCollectEntry> {
        val entries = mutableListOf<KazumiCollectEntry>()
        var offset = 0
        while (offset + 8 <= bytes.size) {
            val frameStart = offset
            val frameLength = readUint32Le(bytes, offset)
            // frameLength 至少 8（4 长度 + 4 CRC），且不越界
            if (frameLength < 8 || frameStart + frameLength > bytes.size) {
                offset = frameStart + 1
                continue
            }
            val crcOffset = frameStart + frameLength - 4
            val storedCrc = readUint32Le(bytes, crcOffset)
            val computedCrc = crc32(bytes, frameStart, frameLength - 4)
            if (storedCrc != computedCrc) {
                offset = frameStart + 1
                continue
            }
            // 帧内容 = [frameLength-4] 内：key + value（不含 CRC）
            val contentEnd = crcOffset
            try {
                val reader = FrameReader(bytes, frameStart + 4, contentEnd)
                val key = reader.readKey() // 一般是 int (bangumiId)
                val value = reader.readValue()
                @Suppress("UNCHECKED_CAST")
                if (value is Map<*, *>) {
                    entries.add(parseCollectedBangumi(key, value))
                }
            } catch (_: Exception) {
                // 单帧解析失败不影响整体
            }
            offset = crcOffset + 4
        }
        return entries
    }

    private fun parseCollectedBangumi(key: Any?, fields: Map<*, *>): KazumiCollectEntry {
        // CollectedBangumi 字段：0=BangumiItem、1=DateTime(millis)、2=type(int)
        val bangumi = fields[0] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val timeMillis = (fields[1] as? Number)?.toLong() ?: 0L
        val collectType = (fields[2] as? Number)?.toInt() ?: 0
        return KazumiCollectEntry(
            key = (key as? Number)?.toLong() ?: 0L,
            bangumiId = (bangumi[0] as? Number)?.toLong() ?: 0L,
            type = (bangumi[1] as? Number)?.toInt() ?: 0,
            title = bangumi[2] as? String ?: "",
            nameCn = bangumi[3] as? String ?: "",
            summary = bangumi[4] as? String ?: "",
            airDate = bangumi[5] as? String ?: "",
            airWeekday = (bangumi[6] as? Number)?.toInt() ?: 0,
            rank = (bangumi[7] as? Number)?.toInt() ?: 0,
            images = (bangumi[8] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to (v as? String ?: "") } ?: emptyMap(),
            tags = (bangumi[9] as? List<*>)?.mapNotNull { tag ->
                (tag as? Map<*, *>)?.let { it[0] as? String } ?: ""
            }?.filter { it.isNotBlank() } ?: emptyList(),
            alias = (bangumi[10] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            ratingScore = (bangumi[11] as? Number)?.toDouble() ?: 0.0,
            votes = (bangumi[12] as? Number)?.toInt() ?: 0,
            votesCount = (bangumi[13] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
            info = bangumi[14] as? String ?: "",
            collectTimeMillis = timeMillis,
            collectType = collectType,
        )
    }

    // ==================== 底层二进制读取 ====================

    /** 有序 frame 解析器：只关心当前帧内容（[start, end) 不含 CRC）。 */
    private class FrameReader(private val data: ByteArray, start: Int, private val end: Int) {
        var pos: Int = start

        fun readKey(): Any? {
            val keyType = readByte()
            return when (keyType) {
                0 -> readUint32() // int key
                1 -> {
                    val len = readByte()
                    readUtf8(len)
                }
                else -> { skip(1); null }
            }
        }

        fun readValue(): Any? = readValueTyped()

        private fun readValueTyped(): Any? {
            val typeId = readTypeId()
            return when (typeId) {
                NULL_T -> null
                INT_T -> readDouble().toLong()
                DOUBLE_T -> readDouble()
                BOOL_T -> readByte() != 0
                STRING_T -> readString()
                BYTE_LIST_T -> readBytes()
                INT_LIST_T -> readDoubleList().map { it.toInt() }
                DOUBLE_LIST_T -> readDoubleList()
                BOOL_LIST_T -> {
                    val n = readUint32()
                    (0 until n).map { readByte() != 0 }
                }
                STRING_LIST_T -> readStringList()
                LIST_T -> readList()
                MAP_T -> readMap()
                HIVE_LIST_T -> emptyList<Any?>()
                INT_SET_T, DOUBLE_SET_T, STRING_SET_T, SET_T -> emptySet<Any>()
                DATE_TIME -> readDouble().toLong() // millis
                DATE_TIME_WITH_TZ -> {
                    // hive_ce DateTimeWithTZ 负载 = 8B double(epoch millis) + 1B 时区偏移；
                    // 少读 1 字节会导致后续字段整体错位（实测 collectibles.tmp 已验证）
                    val millis = readDouble().toLong()
                    skip(1)
                    millis
                }
                BIG_INT, DURATION -> { skip(8); 0 }
                TYPE_BANGUMI_ITEM, TYPE_COLLECTED_BANGUMI, TYPE_BANGUMI_TAG -> readObject(typeId)
                else -> throw IllegalStateException("Unknown typeId $typeId")
            }
        }

        /**
         * 对象读取：typeId 前缀后的字节 = [numOfFields byte][fieldId byte + value]...
         * 返回 Map<fieldId, value>（值递归 readValue）。
         */
        private fun readObject(typeId: Int): Map<Int, Any?> {
            val numFields = readByte()
            if (numFields > 100) throw EOFException("implausible numFields=$numFields")
            val fields = HashMap<Int, Any?>()
            for (i in 0 until numFields) {
                val fieldId = readByte()
                fields[fieldId] = readValue()
            }
            return fields
        }

        private fun readTypeId(): Int {
            val typeId = readByte()
            return if (typeId == TYPE_ID_EXTENSION) readWord() else typeId
        }

        private fun readList(): List<Any?> {
            val n = readUint32()
            return (0 until n).map { readValue() }
        }

        private fun readMap(): Map<Any?, Any?> {
            val n = readUint32()
            val map = HashMap<Any?, Any?>()
            for (i in 0 until n) {
                val k = readValue()
                val v = readValue()
                map[k] = v
            }
            return map
        }

        private fun readStringList(): List<String> {
            val n = readUint32()
            return (0 until n).map { readString() }
        }

        private fun readDoubleList(): List<Double> {
            val n = readUint32()
            return (0 until n).map { readDouble() }
        }

        private fun readBytes(): ByteArray {
            val n = readUint32()
            require(n <= remaining()) { "byte list length out of range" }
            val out = ByteArray(n)
            System.arraycopy(data, pos, out, 0, n)
            pos += n
            return out
        }

        private fun readUtf8(len: Int): String {
            require(len in 0..remaining())
            val s = String(data, pos, len, Charsets.UTF_8)
            pos += len
            return s
        }

        private fun readString(): String {
            val n = readUint32()
            return readUtf8(n)
        }

        private fun readDouble(): Double {
            require(8)
            val v = ByteBuffer.wrap(data, pos, 8).order(ByteOrder.LITTLE_ENDIAN).double
            pos += 8
            return v
        }

        private fun readUint32(): Int {
            require(4)
            val v = readUint32Le(data, pos)
            pos += 4
            return v
        }

        private fun readByte(): Int {
            require(1)
            return data[pos++].toInt() and 0xFF
        }

        private fun readWord(): Int {
            require(2)
            val v = readByte() or (readByte() shl 8)
            return v
        }

        private fun require(n: Int) {
            if (pos + n > end) throw EOFException("frame content out of range")
        }

        private fun remaining(): Int = end - pos

        private fun skip(n: Int) {
            require(n)
            pos += n
        }
    }

    private fun readUint32Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    /** Dart Crc32 一致：标准 CRC-32（多项式 0x04C11DB7，初始 0，final xor）。 */
    private fun crc32(bytes: ByteArray, offset: Int, length: Int): Int {
        val crc = CRC32()
        crc.update(bytes, offset, length)
        return crc.value.toInt()
    }
}

/** Kazumi 收藏条目（解析结果）。 */
data class KazumiCollectEntry(
    val key: Long,
    val bangumiId: Long,
    val type: Int,               // Bangumi 类型（1=book 2=anime 3=music 4=game 5=real）
    val title: String,
    val nameCn: String,
    val summary: String,
    val airDate: String,
    val airWeekday: Int,
    val rank: Int,
    val images: Map<String, String>,
    val tags: List<String>,
    val alias: List<String>,
    val ratingScore: Double,
    val votes: Int,
    val votesCount: List<Int>,
    val info: String,
    val collectTimeMillis: Long,
    val collectType: Int,        // Kazumi 收藏类型（1 在看 2 想看 3 搁置 4 看过 5 抛弃）
) {
    val isAnime: Boolean get() = type == 2
}