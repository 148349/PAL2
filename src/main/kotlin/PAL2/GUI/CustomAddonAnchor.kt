package PAL2.GUI

import GlobalData
import PAL2.Addons.Externals
import PAL2.Database.hideExternalAddon
import PAL2.Database.updateExternalAddon
import PAL2.Database.updateRunOnLaunchExternal
import PAL2.SystemHandling.FileDownloader
import PAL2.SystemHandling.taskKill
import PAL_DataClasses.PAL_External_Addon
import SystemHandling.deleteFile
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.control.CheckBox
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.effect.Lighting
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

/**
 *
 */
private val logger = KotlinLogging.logger {}

class ExternalAnchor(val externalAddon: PAL_External_Addon)
{
    var anchorPane = AnchorPane()
    var displayImage = ImageView()
    lateinit var textAddonName: Text
    lateinit var textNewestVersion: Text
    lateinit var textLastCheck: Text
    lateinit var textEnabled: Text

    lateinit var bTextVersion: Text
    lateinit var bTextLastCheck: Text
    lateinit var bTextNewVersion: Text

    lateinit var checkBox: CheckBox

    //var imageViewInfo = ImageView()

    var anchorButton = AnchorPane()
    var rectButton = Rectangle(75.0, 25.0)
    var textButton = Text()

    // Context Menu
    val cUpdate = MenuItem("Check for update")
    val update = MenuItem("Update")
    val settings = MenuItem("Settings")
    val remove = MenuItem("Remove")
    val editMenu = ContextMenu(cUpdate, update, settings, remove)

    init
    {
        anchorPane.id = externalAddon.eid.toString()
        initImg()
        initTopText()
        initBottomText()
        initButton()
        initCheckBox()
        initEditMenu()

        setListners()
        updateChecker()
    }

    private fun initEditMenu()
    {
        editMenu.onHidden = EventHandler{GlobalData.contextMenuOpen = false}
        cUpdate.onAction = EventHandler{ GlobalScope.launch { updateChecker() } }
        settings.onAction = EventHandler { CoreApplication.controller.showSettingsOfExternal(externalAddon) }
        update.onAction = EventHandler { updateExternal() }
        remove.onAction = EventHandler {
            val aid = Externals.isMajorAddon(externalAddon.webSource)
            if (aid != 0)
                CoreApplication.controller.setDownloadableAddon(aid)
            hideExternalAddon(externalAddon.eid)
            CoreApplication.controller.removeExternalSelected()
        }
    }

    private fun updateExternal()
    {
        // TaskKill
        val ext = File(externalAddon.path).extension

        when (ext)
        {
            "ahk" -> taskKill("autohotkey.exe").waitFor()
            "jar" -> taskKill("java").waitFor()
            "exe" -> taskKill(File(externalAddon.path).name).waitFor()
        }


        GlobalScope.launch {
            CoreApplication.controller.showDownloadPopup(File(externalAddon.webSource).name)
            val location = FileDownloader().downloadFile(URL(externalAddon.webSource), GlobalData.temp_down_folder, 1024, GlobalData.noIcon)
            val dest = File(externalAddon.path)
            deleteFile(dest)
            Files.copy(location.toPath(), dest.toPath())

            // Set new CRC32
            GlobalScope.launch {
                val crc32 = Externals.calcCRC32(dest)
                externalAddon.checksum = crc32
                bTextNewVersion.text = crc32
                updateExternalAddon(externalAddon)
            }


            // Set Icon to green
            Platform.runLater { displayImage.effect = LightningEffects.noUpdateLighting() }
        }
    }

    private fun updateChecker()
    {
        if (externalAddon.webSource.isEmpty())
            return

        Platform.runLater { displayImage.effect = Lighting() }

        val httpConnection = URL(externalAddon.webSource).openConnection() as HttpURLConnection
        httpConnection.addRequestProperty("User-Agent", "Mozilla/4.0")
        val input = BufferedInputStream(httpConnection.inputStream)
        val crc32 = Externals.calcCRC32(input.readBytes())

        if (crc32 == externalAddon.checksum)
        {
            Platform.runLater { displayImage.effect = LightningEffects.noUpdateLighting() }
        }
        else
        {
            Platform.runLater { displayImage.effect = LightningEffects.updateAvailLighting() }
        }
    }

    private fun initCheckBox()
    {
        checkBox = CheckBox("")
        anchorPane.children.add(checkBox)
        checkBox.layoutX = 542.5
        checkBox.layoutY = 20.0
        checkBox.isSelected = externalAddon.runOnLaunch
    }

    private fun setListners()
    {
        anchorListner()
        setDownloadUpdateListner()
        checkBoxListener()
    }

    private fun checkBoxListener()
    {
        checkBox.onMouseClicked = EventHandler()
        {
            updateRunOnLaunchExternal(externalAddon.eid, checkBox.isSelected)
        }
    }


    fun anchorListner()
    {
        // TODO: Create Rollbock Update Option

        anchorPane.onMouseClicked = EventHandler()
        {
            if (it.button == MouseButton.PRIMARY)
            {
                if (it.clickCount == 2)
                {
                    // TODO: If no launch command show a popup that says that there is no launch command.
                    GlobalScope.launch {
                        Runtime.getRuntime().exec(externalAddon.launchCMD)
                    }
                }
            }
        }
        anchorPane.onContextMenuRequested = EventHandler()
        {
            if (!GlobalData.contextMenuOpen)
            {
                GlobalData.contextMenuOpen = true
                editMenu.show(anchorPane, it.screenX, it.screenY)
            }
        }
    }

    fun setDownloadUpdateListner()
    {

        rectButton.onMouseEntered = EventHandler()
        {
            rectButton.stroke = Color(1.0, 0.0, 1.0, 1.0)
        }
        rectButton.onMouseExited = EventHandler()
        {
            rectButton.stroke = Color.WHITE
        }
        rectButton.onMouseClicked = EventHandler()
        {
            Platform.runLater {
                CoreApplication.controller.showSettingsOfExternal(externalAddon)
            }
        }
    }


    private fun initButton()
    {
        textButton.id = "textButton"
        rectButton.id = "rectButton"
        initButton("Settings", anchorButton, textButton, rectButton)
        anchorPane.children.add(anchorButton)
    }

    private fun initButton(arg: String, anchor: AnchorPane, text: Text, rect: Rectangle)
    {
        anchor.layoutX = 240.0
        anchor.layoutY = 0.0

        text.text = arg
        anchor.children.add(text)
        text.textAlignment = TextAlignment.CENTER
        text.layoutX = 0.0
        text.layoutY = 25.0
        text.wrappingWidth = 75.0
        text.fill = Color.WHITE

        anchor.children.add(rect)
        rect.layoutX = 0.0
        rect.layoutY = 7.5
        rect.stroke = Color.WHITE
        rect.fill = Color(1.0, 1.0, 1.0, 0.0)
    }

    // Drive letter :/ FileName.ext
    fun shortner(maxChars: Int, string: String): String
    {
        if (string.length < maxChars)
            return string

        val f = File(string)
        val splits = string.split(File.separator)

        return if (splits[0].length + splits[splits.size-1].length < maxChars - 5)
        {
            "${splits[0]}${File.separator}...${File.separator}${splits[splits.size-1]}"
        }
        else
        {
            when
            {
                f.name.length < maxChars -> f.name
                f.nameWithoutExtension.length < maxChars -> f.nameWithoutExtension
                else -> ""
            }
        }

    }

    private fun initBottomText()
    {
        // TODO: middle ... for x size
        bTextVersion = bottomTextFactory(shortner(50, externalAddon.path), 40.0, 200.0)
        bTextVersion.textAlignment = TextAlignment.LEFT
        bTextVersion.id = "bTextVersion"
        bTextLastCheck = bottomTextFactory(monthToNum(externalAddon.eid.toString()), 425.0, 100.0)
        bTextLastCheck.id = "bTextLastCheck"

        bTextNewVersion = bottomTextFactory(externalAddon.checksum, 325.0, 100.0)

        anchorPane.children.addAll(bTextVersion, bTextLastCheck, bTextNewVersion)
    }

    private fun monthToNum(arg: String): String
    {
        when (true)
        {
            arg.toLowerCase().contains("january") -> return arg.replace("JANUARY", "01")
            arg.toLowerCase().contains("february") -> return arg.replace("FEBRUARY", "02")
            arg.toLowerCase().contains("march") -> return arg.replace("MARCH", "03")
            arg.toLowerCase().contains("april") -> return arg.replace("APRIL", "04")
            arg.toLowerCase().contains("may") -> return arg.replace("MAY", "05")
            arg.toLowerCase().contains("june") -> return arg.replace("JUNE", "06")
            arg.toLowerCase().contains("july") -> return arg.replace("july", "07")
            arg.toLowerCase().contains("august") -> return arg.replace("AUGUST", "08")
            arg.toLowerCase().contains("september") -> return arg.replace("SEPTEMBER", "09")
            arg.toLowerCase().contains("october") -> return arg.replace("OCTOBER", "10")
            arg.toLowerCase().contains("november") -> return arg.replace("NOVEMBER", "11")
            arg.toLowerCase().contains("december") -> return arg.replace("DECEMBER", "12")
            else -> return arg
        }
    }


    private fun initTopText()
    {
        textAddonName = textTopFactory(externalAddon.name, 40.0, 200.0)
        textAddonName.id = "textAddonName"
        textAddonName.textAlignment = TextAlignment.LEFT
        textNewestVersion = textTopFactory("CRC32", 325.0, 100.0)
        textLastCheck = textTopFactory("EID", 425.0, 100.0)
        textEnabled = textTopFactory("Enabled", 525.0, 50.0)

        anchorPane.children.addAll(textAddonName, textNewestVersion, textLastCheck, textEnabled)
    }

    fun bottomTextFactory(arg: String, x: Double, ww: Double): Text
    {
        val text = Text()
        text.layoutX = x
        text.layoutY = 32.5
        text.wrappingWidth = ww
        text.textAlignment = TextAlignment.CENTER
        text.text = arg
        text.fill = Color.WHITE
        return text
    }

    fun textTopFactory(arg: String, x: Double, ww: Double): Text
    {
        val text = Text()
        text.fill = Color.WHITE
        text.layoutX = x
        text.layoutY = 15.0
        text.style = "-fx-font-weight: Bold;"
        text.text = arg
        text.wrappingWidth = ww
        text.textAlignment = TextAlignment.CENTER
        return text
    }

    fun initImg()
    {
        /*
        imageViewInfo.layoutX = 530.0
        imageViewInfo.layoutY = 2.5
        imageViewInfo.fitHeight = 35.0
        imageViewInfo.fitWidth = 35.0
        imageViewInfo.image = Image(javaClass.getResource("/icons/gggAproveQ.png").openStream())
        anchorPane.children.add(imageViewInfo)*/

        displayImage.layoutX = 2.5
        displayImage.layoutY = 2.5
        displayImage.fitWidth = 35.0
        displayImage.fitHeight = 35.0
        displayImage.id = "imageInfo"

        logger.debug{"${externalAddon.name} | ${externalAddon.iconUrl}"}

        // TODO: Use IconURL

        displayImage.image = Image(javaClass.getResource("/icons/NoIcon.png").openStream())
        anchorPane.children.add(displayImage)
    }
}