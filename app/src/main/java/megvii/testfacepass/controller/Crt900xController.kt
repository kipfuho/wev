package megvii.testfacepass.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.crt.Crt900x
import com.crt.c900xtools.MtDefault
import kotlinx.coroutines.*
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.ResponseAPDU
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.LDSFileUtil
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.COMFile
import org.jmrtd.lds.icao.DG2File
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Arrays


object Utils {

    /* ================= CARD TYPES ================= */

    const val CARD_MIFARE_S50 = 1
    const val CARD_MIFARE_S70 = 2
    const val CARD_MIFARE_ULTRALIGHT = 3
    const val CARD_TYPE_A_CPU = 4
    const val CARD_TYPE_B_CPU = 5
    const val CARD_IDENTITY_CARD = 6
    const val CARD_NOT_CARD = 0
    const val CARD_UNKNOWN = 9

    fun getCardTypeText(cardType: Int): String =
        when (cardType) {
            CARD_MIFARE_S50 -> "Mifare S50卡"
            CARD_MIFARE_S70 -> "Mifare S70卡"
            CARD_MIFARE_ULTRALIGHT -> "Mifare UltraLight卡"
            CARD_TYPE_A_CPU -> "非接Type-A CPU卡"
            CARD_TYPE_B_CPU -> "非接Type-B CPU卡"
            CARD_IDENTITY_CARD -> "二代证"
            CARD_NOT_CARD -> "无卡"
            else -> "未知卡"
        }

    /* ================= STRING ================= */

    /**
     * Trim spaces and remove all internal spaces
     * (Compose-safe: String only)
     */
    fun trimAll(value: String?): String {
        if (value == null) return ""
        return value.trim().replace(" ", "")
    }

    /* ================= HEX ================= */

    private const val HEX_STR = "0123456789ABCDEF"

    /**
     * Hex string → byte array
     * Example: "0A1B" → [0x0A, 0x1B]
     */
    fun hexStr2ByteArrs(hexString: String): ByteArray {
        val temp = hexString.uppercase()
        val len = temp.length / 2
        val bytes = ByteArray(len)

        for (i in 0 until len) {
            val high = HEX_STR.indexOf(temp[i * 2]) shl 4
            val low = HEX_STR.indexOf(temp[i * 2 + 1])
            bytes[i] = (high or low).toByte()
        }
        return bytes
    }

    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

    fun byteToHex(b: Byte): String {
        val v = b.toInt() and 0xFF
        return "" + HEX_ARRAY[v ushr 4] + HEX_ARRAY[v and 0x0F]
    }

    /**
     * Byte array → hex string
     */
    fun bytes2HexStr(bytes: ByteArray, len: Int, space: Boolean): String {
        if (len > bytes.size) return ""

        val sb = StringBuilder(len * if (space) 3 else 2)
        for (i in 0 until len) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(HEX_ARRAY[v ushr 4])
            sb.append(HEX_ARRAY[v and 0x0F])
            if (space) sb.append(' ')
        }
        return sb.toString().trim()
    }

    /**
     * Expanded hex display:
     * first 256 bytes + last 16 bytes
     */
    fun bytes2HexStrExpend(bytes: ByteArray, len: Int, space: Boolean): String {
        val maxDisplay = 256
        val endDisplay = 16
        val realLen = minOf(len, bytes.size)

        val sb = StringBuilder()

        val firstPart = minOf(realLen, maxDisplay)
        for (i in 0 until firstPart) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(HEX_ARRAY[v ushr 4])
            sb.append(HEX_ARRAY[v and 0x0F])
            if (space && i < firstPart - 1) sb.append(' ')
        }

        if (realLen > maxDisplay) {
            sb.append(" ... ... ")
            for (i in realLen - endDisplay until realLen) {
                val v = bytes[i].toInt() and 0xFF
                sb.append(HEX_ARRAY[v ushr 4])
                sb.append(HEX_ARRAY[v and 0x0F])
                if (space && i < realLen - 1) sb.append(' ')
            }
        }

        return sb.toString()
    }

    fun extractTlvValue(data: ByteArray, length: Int): ByteArray {
        var idx = 0

        // Tag (1 or 2 bytes usually)
        idx++
        if (data[0].toInt() and 0x1F == 0x1F) {
            idx++ // multi-byte tag
        }

        // Length
        val lenByte = data[idx++].toInt() and 0xFF
        val valueLen = if (lenByte and 0x80 == 0) {
            lenByte
        } else {
            val count = lenByte and 0x7F
            var l = 0
            repeat(count) {
                l = (l shl 8) or (data[idx++].toInt() and 0xFF)
            }
            l
        }

        return data.copyOfRange(idx, idx + valueLen)
    }

    fun parseDG1(resp: ByteArray, respLen: Int): String {
        val value = extractTlvValue(resp, respLen)
        return value.toString(Charsets.US_ASCII)
    }

    fun parseDG2Image(resp: ByteArray, respLen: Int): ByteArray {
        val value = extractTlvValue(resp, respLen)

        // JPEG starts with FF D8
        val start = value.indexOfFirst {
            it == 0xFF.toByte()
        }

        return value.copyOfRange(start, value.size)
    }

    fun parseDG13(resp: ByteArray, respLen: Int): String {
        val value = extractTlvValue(resp, respLen)
        return value.toString(Charsets.UTF_8)
    }
}

class CrtCardService(
    private val reader: Crt900x
) : CardService() {

    override fun open() {
        // Reader already opened via CrtReaderConnect()
        // Nothing to do here
    }

    override fun isOpen(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getATR(): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun close() {
        reader.CrtReaderDisConnect()
    }

    override fun isConnectionLost(e: Exception?): Boolean {
        TODO("Not yet implemented")
    }

    @Throws(CardServiceException::class)
    // MODIFIED to accept nullable CommandAPDU and return nullable ResponseAPDU
    override fun transmit(commandAPDU: CommandAPDU?): ResponseAPDU? {
        // Add a check for a null commandAPDU
        if (commandAPDU == null) {
            throw CardServiceException("CommandAPDU cannot be null")
            // Or alternatively, return null if that's a valid scenario:
            // return null
        }

        val cmd = commandAPDU.bytes
        Log.i("CrtCardService", "Transmit: ${Utils.bytes2HexStr(cmd, cmd.size, true)}")

        val responseBuffer = ByteArray(4096)
        val responseLength = IntArray(1)

        val ret = reader.CrtSendAPDU(
            'A',           // slot (usually 0)
            cmd.size,
            cmd,
            responseLength,
            responseBuffer
        )

        if (ret != 0) {
            throw CardServiceException("CrtSendAPDU failed, ret=$ret")
        }

        val resp = Arrays.copyOf(responseBuffer, responseLength[0])
        return ResponseAPDU(resp)
    }
}

class Crt900xController(context: Context) {

    /* ================= CORE ================= */

    private val reader = Crt900x(context)

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    var testResult by mutableStateOf("")
        private set

    var faceBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var apduCommand by mutableStateOf("00 A4 04 00 07 A0 00 00 02 47 10 01")
        private set

    private fun log(msg: String) {
        Log.i("Crt900xController", msg)
        testResult += msg + "\n"
    }

    /* ================= ACTIONS ================= */

    fun crtReaderConnect() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                reader.CrtReaderDisConnect()
                reader.CrtSetBaseDir("/storage/emulated/0/crt_driver_Log")
                reader.CrtSetLogLevel(2)

                val ret = reader.CrtReaderConnect(
                    MtDefault.READER_PID,
                    MtDefault.READER_VID
                )

                if (ret == 0) {
                    "CrtReaderConnect : OK"
                } else {
                    val err = ByteArray(128)
                    reader.CrtGetLastError(err)
                    "Connect Error: ${String(err)}"
                }
            }
            log(result)
        }
    }

    fun crtReaderCheckCard() {
        scope.launch {
            val ret = withContext(Dispatchers.IO) {
                reader.CrtReaderCheckCard()
            }
            log("CheckCard: $ret (${Utils.getCardTypeText(ret)})")
        }
    }

    fun crtReaderReset() {
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                val buf = ByteArray(64)
                val ret = reader.CrtReaderReset(buf)
                if (ret == 0) {
                    "Reset OK: ${String(buf)}"
                } else {
                    val err = ByteArray(128)
                    reader.CrtGetLastError(err)
                    "Reset Error: ${String(err)}"
                }
            }
            log(msg)
        }
    }

    fun crtReaderRFActive() {
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                val atr = ByteArray(128)
                val len = reader.CrtReaderRFActive(atr)
                if (len > 0) {
                    "RF Active: ${Utils.bytes2HexStr(atr, len, true)}"
                } else {
                    val err = ByteArray(128)
                    reader.CrtGetLastError(err)
                    "RF Active Error: ${String(err)}"
                }
            }
            log(msg)
        }
    }

    fun crtReaderRFRelease() {
        scope.launch {
            val ret = withContext(Dispatchers.IO) {
                reader.CrtReaderRFRelease()
            }
            log("RF Release: $ret")
        }
    }

    fun crtReaderRFReset() {
        scope.launch {
            val ret = withContext(Dispatchers.IO) {
                val out = ByteArray(64)
                reader.CrtReaderReset(out)
            }
            log("RF Reset: $ret")
        }
    }

    fun crtReaderDisConnect() {
        scope.launch {
            val ret = withContext(Dispatchers.IO) {
                reader.CrtCloseReader()
            }
            log("Disconnect: $ret")
        }
    }

    fun crtReaderLEDControl(red: Int, green: Int, blue: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val cmd = byteArrayOf(
                    0x43, 0x30, 0x31,
                    red.toByte(),
                    green.toByte(),
                    blue.toByte()
                )
                val resp = ByteArray(64)
                val respLen = intArrayOf(0)
                reader.CrtExecCommand(cmd.size, cmd, respLen, resp)
            }
            log("LED Control sent ($red,$green,$blue)")
        }
    }

    fun crtReaderReadCardInfoAndPic(
        cardId: String,
        birth: String,
        expiry: String
    ) {
        if(cardId.isEmpty() || birth.isEmpty() || expiry.isEmpty()) return
        log("Read Card Info ...\n")
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                val out = CharArray(1024)
                val ret = reader.CrtReaderReadCardInfo1(
                    cardId, birth, expiry, out
                )
                if (ret == 0) {
                    "Card Info:\n${String(out)}"
                } else {
                    val err = ByteArray(128)
                    reader.CrtGetLastError(err)
                    "Read Error: ${String(err)}"
                }
            }
            log(msg)
        }
    }

    fun crtReaderReadDGCom() {
        scope.launch(Dispatchers.IO) {
            val hexCmds = "436084" // READ DG COM
            val cmds = Utils.hexStr2ByteArrs(hexCmds)
            val respLen = IntArray(1)
            val resp = ByteArray(24 * 2024)

            withContext(Dispatchers.Main) {
                log("Read DG Com ...\n")
            }

            val execRet = reader.CrtExecCommand(
                cmds.size,
                cmds,
                respLen,
                resp
            )

            withContext(Dispatchers.Main) {
                if (execRet == 0) {
                    log(
                        "Recv : ${
                            Utils.bytes2HexStrExpend(resp, respLen[0], true)
                        }\n"
                    )

                    // DG bitmap (same loop logic as Java)
                    for (i in 6 until respLen[0] - 1) {
                        log("DG${i - 5} : ${Utils.byteToHex(resp[i])}\n")
                    }
                    log("\n")
                } else {
                    val err = ByteArray(128)
                    reader.CrtGetLastError(err)
                    log("Error: ${String(err)}\n")
                }
            }
        }
    }

    fun crtReaderReadDG(dgIndex: Int) { // 1-based: DG1 = 1
        scope.launch(Dispatchers.IO) {
            // 436082 + DG index + 00000001
            val hexCmds = "436082%02x00000001".format(dgIndex)
            val cmds = Utils.hexStr2ByteArrs(hexCmds)

            val respLen = IntArray(1)
            val resp = ByteArray(24 * 2024)

            withContext(Dispatchers.Main) {
                log("Read DG$dgIndex ...\n")
            }

            val execRet = reader.CrtExecCommand(
                cmds.size,
                cmds,
                respLen,
                resp
            )

            withContext(Dispatchers.Main) {
                if (execRet == 0) {
                    log(
                        "Recv : ${
                            Utils.bytes2HexStrExpend(resp, respLen[0], true)
                        }\n"
                    )
                    log("Data Len : ${respLen[0]}\n\n")
                    when (dgIndex) {
                        1 -> {
                            val mrz = Utils.parseDG1(resp, respLen[0])
                            log("DG1 (MRZ):\n$mrz\n")
                        }
                        2 -> {
                            val imageBytes = Utils.parseDG2Image(resp, respLen[0])
                            faceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        }
                        13 -> {
                            val text = Utils.parseDG13(resp, respLen[0])
                            log("DG13:\n$text\n")
                        }
                        else -> {
                            log("DG$dgIndex raw length: ${respLen[0]}\n")
                        }
                    }
                } else {
                    val err = ByteArray(128)
                    reader.CrtGetLastError(err)
                    log("Error: ${String(err)}\n\n")
                }
            }
        }
    }

    fun crtReaderAPDUTypeA() {
        scope.launch(Dispatchers.IO) {
            val hexCmds = Utils.trimAll(apduCommand)
            val cmds = Utils.hexStr2ByteArrs(hexCmds)
            val respLen = IntArray(1)
            val resp = ByteArray(256)

            val execRet = reader.CrtSendAPDU(
                'A',
                cmds.size,
                cmds,
                respLen,
                resp
            )

            withContext(Dispatchers.Main) {
                if (execRet == 0) {
                    log(
                        "Recv : ${
                            Utils.bytes2HexStrExpend(resp, respLen[0], true)
                        }\n"
                    )
                } else {
                    val err = ByteArray(128)
                    reader.CrtGetLastError(err)
                    log("Error: ${String(err)}\n")
                }
            }
        }
    }

    fun crtReaderAPDUTypeB() {
        scope.launch(Dispatchers.IO) {
            val hexCmds = Utils.trimAll(apduCommand)
            val cmds = Utils.hexStr2ByteArrs(hexCmds)
            val respLen = IntArray(1)
            val resp = ByteArray(256)

            val execRet = reader.CrtSendAPDU(
                'B',
                cmds.size,
                cmds,
                respLen,
                resp
            )

            withContext(Dispatchers.Main) {
                if (execRet == 0) {
                    log(
                        "Recv : ${
                            Utils.bytes2HexStrExpend(resp, respLen[0], true)
                        }\n"
                    )
                } else {
                    val err = ByteArray(128)
                    reader.CrtGetLastError(err)
                    log("Error: ${String(err)}\n")
                }
            }
        }
    }

    fun getCOMandSOD() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                log("Starting BAC")

                val bacKey: BACKeySpec = BACKey(
                    OcrResult.no,
                    OcrResult.birthDate,
                    OcrResult.expiryDate
                )

                reader.CrtReaderRFRelease()
                Thread.sleep(150)
                reader.CrtReaderRFActive(ByteArray(1))
                Thread.sleep(150)

                // select eMRTD application AID
                val hexCmds = Utils.trimAll("00 A4 04 00 07 A0 00 00 02 47 10 01")
                val cmds = Utils.hexStr2ByteArrs(hexCmds)
                val respLen = IntArray(1)
                val resp = ByteArray(256)

                reader.CrtSendAPDU(
                    'A',
                    cmds.size,
                    cmds,
                    respLen,
                    resp
                )

                val cardService = CrtCardService(reader)

                val passportService = PassportService(
                    cardService,
                    256,
                    256,
                    224,
                    false,
                    true
                )


                passportService.open()
                passportService.doBAC(bacKey)
                log("BAC SUCCESS")
                passportService.sendSelectApplet(true)
                // ---- EF.COM ----
                val comStream = passportService.getInputStream(PassportService.EF_COM)
                val comBytes = comStream.readBytes()
                comStream.close()

                // Base64 output (THIS matches your example)
                val comBase64 = Base64.encodeToString(comBytes, Base64.NO_WRAP)
                log("COM: $comBase64")

                // ---- EF.SOD ----
                @Suppress("DEPRECATION")
                val sodStream = passportService.getInputStream(PassportService.EF_SOD)
                val sodBytes = sodStream.readBytes()
                sodStream.close()

                // Base64 output
                val sodBase64 = Base64.encodeToString(sodBytes, Base64.NO_WRAP)
                log("SOD: $sodBase64")
            } catch (e: Exception) {
                log("BAC FAILED, $e")
            }
        }
    }

    fun dgNumberFromTag(tag: Int): Int = when (tag) {
        0x61 -> 1
        0x75 -> 2
        0x63 -> 3
        0x76 -> 4
        in 0x65..0x70 -> tag - 0x60
        else -> -1
    }

    fun normalizeDate(d: String): String {
        val p = d.split("/")
        return "${p[2]}/${p[1]}/${p[0]}"
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getAllData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                log("Starting BAC")

                val bacKey: BACKeySpec = BACKey(
                    OcrResult.no,
                    OcrResult.birthDate,
                    OcrResult.expiryDate
                )

                reader.CrtReaderRFRelease()
                Thread.sleep(150)
                reader.CrtReaderRFActive(ByteArray(1))
                Thread.sleep(150)

                // select eMRTD application AID
                val hexCmds = Utils.trimAll("00 A4 04 00 07 A0 00 00 02 47 10 01")
                val cmds = Utils.hexStr2ByteArrs(hexCmds)
                val respLen = IntArray(1)
                val resp = ByteArray(256)

                reader.CrtSendAPDU(
                    'A',
                    cmds.size,
                    cmds,
                    respLen,
                    resp
                )

                val cardService = CrtCardService(reader)

                val passportService = PassportService(
                    cardService,
                    256,
                    256,
                    224,
                    false,
                    true
                )


                passportService.open()
                passportService.doBAC(bacKey)
                log("BAC SUCCESS")
                passportService.sendSelectApplet(true)

                val verifyObjectData = JSONObject()
                val rawObject = JSONObject()
                // ---- EF.COM ----
                val comBytes = passportService
                    .getInputStream(PassportService.EF_COM)
                    .use { it.readBytes() }
                val comFile = COMFile(ByteArrayInputStream(comBytes))
                val comBase64 = Base64.encodeToString(comBytes, Base64.NO_WRAP)
                log("COM: $comBase64")
                rawObject.put("com", comBase64)

                // ---- EF.SOD ----
                @Suppress("DEPRECATION")
                val sodBytes = passportService
                    .getInputStream(PassportService.EF_SOD)
                    .use { it.readBytes() }
                val sodFile = SODFile(ByteArrayInputStream(sodBytes))
                val sodBase64 = Base64.encodeToString(sodBytes, Base64.NO_WRAP)
                log("SOD: $sodBase64")
                rawObject.put("sod", sodBase64)

                // ---- DG ----
                val dgTags: IntArray = comFile.tagList
                dgTags.forEach { tag ->
                    val fid = LDSFileUtil.lookupFIDByTag(tag)

                    if (tag == 0x63 || tag == 0x76) {
                        log("Skipping EAC-protected DG: 0x${tag.toString(16)}")
                        return@forEach
                    }

                    passportService.getInputStream(fid).use { dgIn ->
                        val dgName = "dg${dgNumberFromTag(tag)}"
                        val bytes = dgIn.readBytes()
                        val dgBase64 = Base64.encodeToString(bytes,Base64.NO_WRAP)
                        log("$dgName $dgBase64")
                        rawObject.put(dgName, dgBase64)
                    }
                }

                // general object data
                verifyObjectData.put("raw", rawObject)
                val dataObject = JSONObject()
                val encodedVerifyObject = Base64.encodeToString(
                    verifyObjectData.toString().toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                dataObject.put("dataVerifyObject", encodedVerifyObject)

                // portrait
                val dg2Bytes = passportService
                    .getInputStream(PassportService.EF_DG2)
                    .use { it.readBytes() }

                val dg2File = DG2File(ByteArrayInputStream(dg2Bytes))
                val faceImageInfo = dg2File.faceInfos[0].faceImageInfos[0]
                val imageBytes = faceImageInfo.imageInputStream.use {
                    it.readBytes()
                }
                dataObject.put("nfcPortrait", Base64.encodeToString(imageBytes,Base64.NO_WRAP))
                val dgCertBase64 = Base64.encodeToString(
                    sodFile.docSigningCertificate.encoded,
                    Base64.NO_WRAP
                )
                dataObject.put("dgCert", dgCertBase64)

                // identity data object
                val identityData = JSONObject()
                val dg13Bytes = passportService
                    .getInputStream(PassportService.EF_DG13)
                    .use { it.readBytes() }
                val dg13Base64 = Base64.encodeToString(dg13Bytes,Base64.NO_WRAP);
                val identityValues = IdentityDecoder.parse()

                identityData.put("mrz", "")
                identityData.put("mrz", "")
                identityData.put("mrz", "")
                identityData.put("mrz", "")
                identityData.put("mrz", "")
                identityData.put("mrz", "")
                identityData.put("mrz", "")
                identityData.put("mrz", "")
                identityData.put("mrz", "")
                identityData.put("mrz", "")
            } catch (e: Exception) {
                log("BAC FAILED, $e")
            }
        }
    }

    fun test() {
        Log.i("Identity", ": ${IdentityDecoder.parse()}")
    }

    /* ================= COMPOSE UI ================= */

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Layout() {
        val scroll = rememberScrollState()

        var selectedDG by remember { mutableStateOf(2) }
        var dgMenuExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            /* ================= CONNECTION ================= */

            Text("Reader", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = { crtReaderConnect() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect")
            }

            Button(
                onClick = { crtReaderDisConnect() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect")
            }

            Button(
                onClick = { test() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test")
            }

            Divider()

            /* ================= RF ================= */

            Text("RF", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { crtReaderRFActive() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("RF Active")
                }

                Button(
                    onClick = { crtReaderRFRelease() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("RF Release")
                }

                Button(
                    onClick = { crtReaderRFReset() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("RF Reset")
                }
            }

            Button(
                onClick = { crtReaderCheckCard() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check Card Type")
            }

            Divider()

            /* ================= PASSPORT ================= */

            Text("Passport", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = {
                    crtReaderReadCardInfoAndPic(
                        cardId = OcrResult.no,
                        birth = OcrResult.birthDate,
                        expiry = OcrResult.expiryDate
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Read Card Info (BAC)")
            }

            Divider()

            /* ================= DG ================= */

            Text("Data Groups", style = MaterialTheme.typography.titleMedium)

            // DG selector
            ExposedDropdownMenuBox(
                expanded = dgMenuExpanded,
                onExpandedChange = { dgMenuExpanded = !dgMenuExpanded }
            ) {
                OutlinedTextField(
                    value = "DG$selectedDG",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select DG") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dgMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = dgMenuExpanded,
                    onDismissRequest = { dgMenuExpanded = false }
                ) {
                    (1..15).forEach { dg ->
                        DropdownMenuItem(
                            text = { Text("DG$dg") },
                            onClick = {
                                selectedDG = dg
                                dgMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = { crtReaderReadDGCom() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Read DG COM")
            }

            Button(
                onClick = { crtReaderReadDG(selectedDG) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Read DG$selectedDG")
            }

            Divider()

            /* ================= DG IMAGE ================= */

            if (faceBitmap != null) {
                Text("Face Image (DG2)", style = MaterialTheme.typography.titleMedium)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp
                ) {
                    Image(
                        bitmap = faceBitmap!!.asImageBitmap(),
                        contentDescription = "Passport Face",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Divider()
            }

            OutlinedTextField(
                value = apduCommand,
                onValueChange = { apduCommand = it },
                label = { Text("APDU Command") }
            )

            Button(
                onClick = { crtReaderAPDUTypeA() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exec APDU Type A")
            }

            Button(
                onClick = { crtReaderAPDUTypeB() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exec APDU Type B")
            }

            Button(
                onClick = { getCOMandSOD() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Read COM + SOD")
            }

            Button(
                onClick = { getAllData() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get all data")
            }

            /* ================= OUTPUT ================= */

            Text("Output", style = MaterialTheme.typography.titleMedium)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = testResult,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    fun FaceImageView(bitmap: Bitmap?) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "DG2 Face",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}
